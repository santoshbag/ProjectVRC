package com.apollo.analytics.common.calculators.pv01;

import com.apollo.analytics.common.datetime.DayCount;
import com.apollo.analytics.common.datetime.Period;
import com.apollo.analytics.common.datetime.calculators.DCFCalculator;
import com.apollo.analytics.common.datetime.calculators.DCFCalculatorFactory;
import com.apollo.analytics.common.datetime.holidays.HolidayCalendar;
import com.apollo.analytics.common.domain.CashFlowDetail;
import com.apollo.analytics.common.domain.ExtrapolationType;
import com.apollo.analytics.common.domain.Interpolation;
import com.apollo.analytics.common.domain.TBadDayConvention;
import com.apollo.analytics.common.exceptions.ApolloAnalyticsLibraryException;
import com.apollo.analytics.common.lib.core.matrix.IndexMatrix;
import com.apollo.analytics.common.lib.core.matrix.impl.DefaultIndexMatrix;
import com.apollo.analytics.common.utils.Constants;
import com.apollo.analytics.common.utils.DateHolder;
import com.apollo.analytics.common.utils.ReturnStatus;
import com.apollo.analytics.discountfactor.calculators.DiscountFactorCalculatorTaskWrapper;
import com.apollo.analytics.fx.calculators.FxRateCalculatorTaskWrapper;
import com.apollo.analytics.isdamodel.cds.domain.TDateInterval;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static com.apollo.analytics.common.utils.ExcelDayCountFunctions.*;
import static com.apollo.analytics.isdamodel.cds.domain.TDateFunctions.adjustedBusinessDay;
import static com.apollo.analytics.isdamodel.cds.domain.TDateFunctions.dtFwdAny;

/**
 * Created by aanand on 6/5/2015.
 */


public class Pv01Calculator {

    @Resource
    DCFCalculatorFactory dcfCalculatorFactory;

    @Resource
    DiscountFactorCalculatorTaskWrapper discountFactorCalculatorTaskWrapper;

    @Resource
    FxRateCalculatorTaskWrapper fxRateCalculatorTaskWrapper;

    private final static ExtrapolationType extrapolationType = ExtrapolationType.CONTINUOUS_FORWARD;

    public double pv01Old(final LocalDate curveDate,
                          final LocalDate valueDate,
                          final LocalDate startDate,
                          final LocalDate maturityDate,
                          final TDateInterval dateInterval,
                          final String refCcy,
                          final DayCount dayCount,
                          final double shiftBps,
                          final Interpolation interpolation,
                          final double floorRate,
                          final boolean cleanPrice,
                          final String curveCcy,
                          final String curveType,
                          final double fxShift,
                          final TBadDayConvention badDayConvention,
                          final HolidayCalendar holidayCalendar) throws Exception {
        final List<LocalDate> couponDates = couponDates(startDate,
                maturityDate,
                dateInterval,
                holidayCalendar,
                badDayConvention);
        Collections.sort(couponDates);

        final List<LocalDate> goodDates = new ArrayList();

        for (int i = 0; i < couponDates.size(); i++) {
            if (couponDates.get(i).isAfter(valueDate))
                goodDates.add(couponDates.get(i));
        }

        final LocalDate[] forwardValueDates = goodDates.toArray(new LocalDate[0]);
        final double dfs[] = discountFactorCalculatorTaskWrapper.discountFactorForDateRange(
                curveDate,
                curveCcy,
                curveType,
                valueDate,
                forwardValueDates,
                dayCount.getBasis(),
                extrapolationType,
                shiftBps,
                floorRate,
                interpolation
        );

        final List<String> colHeaders = new ArrayList<>();
        colHeaders.add(Constants.DiscountFactor);
        colHeaders.add(Constants.FxRate);

        final IndexMatrix<LocalDate, String> matrix = new DefaultIndexMatrix<>(goodDates, colHeaders);
        for (int i = 0; i < forwardValueDates.length; i++) {
            matrix.setValue(forwardValueDates[i], Constants.DiscountFactor, dfs[i]);
        }

        final double constFxRate = 1.;
        for (int i = 0; i < forwardValueDates.length; i++) {
            LocalDate forwardValueDate = forwardValueDates[i];
            double fxRate;
            if (refCcy.equals(curveCcy)) {
                fxRate = constFxRate;
            } else {
                fxRate = fxRateCalculatorTaskWrapper.getFxRate(curveDate,
                        forwardValueDate,
                        refCcy,
                        curveCcy,
                        Interpolation.LINEAR.name(),
                        dayCount.getBasis()
                ).getRate();
            }


            matrix.setValue(forwardValueDate, Constants.FxRate, fxRate);
        }

        final List<LocalDate> previousCouponDates = new ArrayList<>();

        for (LocalDate ld : couponDates) {
            if (ld.isBefore(valueDate) || ld.isEqual(valueDate))
                previousCouponDates.add(ld);
        }


        Collections.sort(previousCouponDates);
        final LocalDate pcd = previousCouponDates.size() == 0 ? forwardValueDates[0] :
                previousCouponDates.get(previousCouponDates.size() - 1);

        double result = 0.0;

        final DCFCalculator dcfCalculator = dcfCalculatorFactory.getCalculator(dayCount);

        for (int i = 0; i < forwardValueDates.length; i++) {
            final LocalDate forwardValueDate = forwardValueDates[i];
            LocalDate previousDate;
            if (i == 0) {
                previousDate = pcd;
            } else {
                previousDate = forwardValueDates[i - 1];
            }

            final double df = matrix.getValue(forwardValueDate, Constants.DiscountFactor);
            final double fxRate = matrix.getValue(forwardValueDate, Constants.FxRate);
            final double dcf = dcfCalculator.dcf(previousDate, forwardValueDate, dayCount).DCF();
            final double tmpValue = df * fxRate * fxShift * dcf;

            result += tmpValue;
        }

        double accrued = 0.0;
        if (cleanPrice) {
            if (valueDate.isAfter(pcd)) {
                //
                //If start date is in the past
                // - use the value date to calculate the accrual
                // - else use the start date
                //
                final LocalDate previousDate = max(valueDate, startDate);
                final DateHolder returnDate = new DateHolder();
                if (adjustedBusinessDay(previousDate, badDayConvention, holidayCalendar, returnDate)
                        .equals(ReturnStatus.FAILURE))
                    throw new ApolloAnalyticsLibraryException("Error calculating adjusted business day");

                final double dcf = dcfCalculator.dcf(pcd,
                        returnDate.get(),
                        dayCount).DCF();
                final double fxRate = fxRateCalculatorTaskWrapper.getFxRate(curveDate,
                        returnDate.get(),
                        refCcy,
                        curveCcy,
                        Interpolation.LINEAR.name(),
                        dayCount.getBasis()).getRate();
                final double df = discountFactorCalculatorTaskWrapper.discountFactorForDate(
                        curveDate,
                        curveCcy,
                        curveType,
                        valueDate,
                        returnDate.get(),
                        dayCount.getBasis(),
                        extrapolationType,
                        shiftBps,
                        floorRate,
                        interpolation);
                accrued = dcf * fxRate * df;

            }
            result -= accrued;
        }


        return result;
    }


    public double pv01(final LocalDate curveDate,
                       final LocalDate valueDate,
                       final LocalDate startDate,
                       final LocalDate maturityDate,
                       final TDateInterval dateInterval,
                       final String refCcy,
                       final DayCount dayCount,
                       final double shiftBps,
                       final Interpolation interpolation,
                       final double floorRate,
                       final boolean cleanPrice,
                       final String curveCcy,
                       final String curveType,
                       final double fxShift,
                       final TBadDayConvention badDayConvention,
                       final HolidayCalendar holidayCalendar) throws Exception {

        double sum = 0.0;
        boolean returnOld = false;
        if (returnOld) {
            return pv01Old(
                    curveDate,
                    valueDate,
                    startDate,
                    maturityDate,
                    dateInterval,
                    refCcy,
                    dayCount,
                    shiftBps,
                    interpolation,
                    floorRate,
                    cleanPrice,
                    curveCcy,
                    curveType,
                    fxShift,
                    badDayConvention,
                    holidayCalendar
            );

        }
        final List<CashFlowDetail> dirtyCashFlowDetails = pv01GridDirty(
                curveDate,
                valueDate,
                startDate,
                maturityDate,
                dateInterval,
                refCcy,
                dayCount,
                shiftBps,
                interpolation,
                floorRate,
                cleanPrice,
                curveCcy,
                curveType,
                fxShift,
                badDayConvention,
                holidayCalendar
        );

        //final List<Period> cashFlowDates = matrix.getRowKeys();

        //dirty price
        for (int i = 0; i < dirtyCashFlowDetails.size(); i++) {
            final Period cashFlowPeriod = dirtyCashFlowDetails.get(i).period;
            final double cashFlow = dirtyCashFlowDetails.get(i).cashFlow;
            sum += cashFlow;
        }

        if (cleanPrice) {
            List<CashFlowDetail> accrualCashFlowDetails = accrualGrid(curveDate,
                    valueDate,
                    startDate,
                    maturityDate,
                    dateInterval,
                    refCcy,
                    dayCount,
                    shiftBps,
                    interpolation,
                    floorRate,
                    cleanPrice,
                    curveCcy,
                    curveType,
                    fxShift,
                    badDayConvention,
                    holidayCalendar);
            double accrualCashFlow = 0.0;

            for (int i = 0; i < accrualCashFlowDetails.size(); i++) {
                final Period accrualPeriod = accrualCashFlowDetails.get(i).period;
                final double cashFlow = accrualCashFlowDetails.get(i).cashFlow;
                accrualCashFlow += cashFlow;
            }

            sum += accrualCashFlow;
        }


        return sum;
    }

    public List<CashFlowDetail> pv01Grid(final LocalDate curveDate,
                                                         final LocalDate valueDate,
                                                         final LocalDate startDate,
                                                         final LocalDate maturityDate,
                                                         final TDateInterval dateInterval,
                                                         final String refCcy,
                                                         final DayCount dayCount,
                                                         final double shiftBps,
                                                         final Interpolation interpolation,
                                                         final double floorRate,
                                                         final boolean cleanPrice,
                                                         final String curveCcy,
                                                         final String curveType,
                                                         final double fxShift,
                                                         final TBadDayConvention badDayConvention,
                                                         final HolidayCalendar holidayCalendar) throws Exception {
        final List<CashFlowDetail> pv01CashFlowsDirty = pv01GridDirty(
                curveDate,
                valueDate,
                startDate,
                maturityDate,
                dateInterval,
                refCcy,
                dayCount,
                shiftBps,
                interpolation,
                floorRate,
                cleanPrice,
                curveCcy,
                curveType,
                fxShift,
                badDayConvention,
                holidayCalendar);

        final List<CashFlowDetail> pv01CashFlows = new ArrayList<>(pv01CashFlowsDirty);

        if (cleanPrice) {
            final List<CashFlowDetail> pv01AccrualCashFlows = accrualGrid(
                    curveDate,
                    valueDate,
                    startDate,
                    maturityDate,
                    dateInterval,
                    refCcy,
                    dayCount,
                    shiftBps,
                    interpolation,
                    floorRate,
                    cleanPrice,
                    curveCcy,
                    curveType,
                    fxShift,
                    badDayConvention,
                    holidayCalendar
            );


            int index = 0;

            for (int i = 0; i < pv01AccrualCashFlows.size(); i++) {
                final CashFlowDetail row = pv01AccrualCashFlows.get(i);
                pv01CashFlows.add(row);
            }
        }

        return pv01CashFlows;
    }

    public List<CashFlowDetail> accrualGrid(final LocalDate curveDate,
                                                   final LocalDate valueDate,
                                                   final LocalDate startDate,
                                                   final LocalDate maturityDate,
                                                   final TDateInterval dateInterval,
                                                   final String refCcy,
                                                   final DayCount dayCount,
                                                   final double shiftBps,
                                                   final Interpolation interpolation,
                                                   final double floorRate,
                                                   final boolean cleanPrice,
                                                   final String curveCcy,
                                                   final String curveType,
                                                   final double fxShift,
                                                   final TBadDayConvention badDayConvention,
                                                   final HolidayCalendar holidayCalendar
    ) throws Exception {
        final List<LocalDate> cashFlowDates =
                cashFlowDates(valueDate, startDate, maturityDate, dateInterval, cleanPrice, badDayConvention, holidayCalendar);

        Collections.sort(cashFlowDates);
        final LocalDate accrualStartDate = cashFlowDates.get(0);
        final LocalDate accrualEndDate = max(valueDate, startDate);

        final DCFCalculator dcfCalculator = dcfCalculatorFactory.getCalculator(dayCount);
        final Period accrualPeriod = new Period(accrualStartDate, accrualEndDate);

        final List<CashFlowDetail> cashFlowDetails = new ArrayList<>();

        if (le(accrualEndDate, accrualStartDate)) {
            final CashFlowDetail cashFlowDetail = new CashFlowDetail(accrualPeriod, 0, 1.0, 1.0, 0.0, refCcy);
            cashFlowDetails.add(cashFlowDetail);
        } else {
            final double dcf = dcfCalculator.dcf(accrualStartDate, accrualEndDate, dayCount).DCF();
            final double df = discountFactorCalculatorTaskWrapper.discountFactorForDate(
                    curveDate,
                    curveCcy,
                    curveType,
                    valueDate,
                    accrualEndDate,
                    dayCount.getBasis(),
                    extrapolationType,
                    shiftBps,
                    floorRate,
                    interpolation
            );

            final double fxRate = fxRateCalculatorTaskWrapper.getFxRate(curveDate,
                    accrualEndDate,
                    refCcy,
                    curveCcy,
                    Interpolation.LINEAR.name(),
                    dayCount.getBasis()
            ).getRate();


            final double cashFlow = dcf * df * fxRate * fxShift * -1;
            final CashFlowDetail cashFlowDetail = new CashFlowDetail(accrualPeriod, dcf, df, fxRate, cashFlow, refCcy);
            cashFlowDetails.add(cashFlowDetail);

        }

        return cashFlowDetails;
    }

    public List<CashFlowDetail> pv01GridDirty(final LocalDate curveDate,
                                                     final LocalDate valueDate,
                                                     final LocalDate startDate,
                                                     final LocalDate maturityDate,
                                                     final TDateInterval dateInterval,
                                                     final String refCcy,
                                                     final DayCount dayCount,
                                                     final double shiftBps,
                                                     final Interpolation interpolation,
                                                     final double floorRate,
                                                     final boolean cleanPrice,
                                                     final String curveCcy,
                                                     final String curveType,
                                                     final double fxShift,
                                                     final TBadDayConvention badDayConvention,
                                                     final HolidayCalendar holidayCalendar) throws Exception {
        final List<LocalDate> cashFlowDates =
                cashFlowDates(valueDate, startDate, maturityDate, dateInterval, cleanPrice, badDayConvention, holidayCalendar);

        final String[] cols = {Constants.DCF, Constants.DiscountFactor, Constants.FxRate, "CashFlow"};
        final DCFCalculator dcfCalculator = dcfCalculatorFactory.getCalculator(dayCount);

        final List<CashFlowDetail> cashFlowDetails = new ArrayList<>();

        final List<Period> periods = new ArrayList<>();

        for (int i = 1; i < cashFlowDates.size(); i++) {
            final Period cashFlowPeriod = new Period(cashFlowDates.get(i - 1), cashFlowDates.get(i));
            periods.add(cashFlowPeriod);
        }


        for (int i = 0; i < periods.size(); i++) {
            final Period cashFlowPeriod = periods.get(i);
            final LocalDate periodEndDate = cashFlowPeriod.endDate();

            final double dcf = dcfCalculator.dcf(cashFlowPeriod.startDate(),
                    cashFlowPeriod.endDate(),
                    dayCount).DCF();

            final double df = discountFactorCalculatorTaskWrapper.discountFactorForDate(
                    curveDate,
                    curveCcy,
                    curveType,
                    valueDate,
                    periodEndDate,
                    dayCount.getBasis(),
                    extrapolationType,
                    shiftBps,
                    floorRate,
                    interpolation
            );

            final double fxRate = fxRateCalculatorTaskWrapper.getFxRate(curveDate,
                    periodEndDate,
                    refCcy,
                    curveCcy,
                    Interpolation.LINEAR.name(),
                    dayCount.getBasis()
            ).getRate();


            final double cashFlow = dcf * df * fxRate * fxShift;

            final CashFlowDetail cashFlowDetail = new CashFlowDetail(cashFlowPeriod, dcf, df, fxRate, cashFlow, refCcy);
            cashFlowDetails.add(cashFlowDetail);
        }

        return cashFlowDetails;
    }


    public List<LocalDate> couponDates(LocalDate startDate,
                                       LocalDate maturityDate,
                                       TDateInterval dateInterval,
                                       HolidayCalendar holidayCalendar,
                                       TBadDayConvention badDayConvention
    ) throws Exception {
        final LocalDate endDate = maturityDate;

        final int dayOfMonth = endDate.getDayOfMonth();
        final int lastDayOfMonth = endDate.with(TemporalAdjusters.lastDayOfMonth()).getDayOfMonth();
        final boolean lastDay = dayOfMonth == lastDayOfMonth ? true : false;

        int numIntervals = 0;
        final List<LocalDate> couponDates = new ArrayList<>();

        final DateHolder lastCouponDate = new DateHolder();
        if (adjustedBusinessDay(endDate, badDayConvention, holidayCalendar, lastCouponDate).equals(ReturnStatus.FAILURE)) {
            throw new ApolloAnalyticsLibraryException("Could not calculate adjusted date for " + endDate);
        }
        couponDates.add(lastCouponDate.get());

        LocalDate tmpDate = endDate;

        while (gt(tmpDate, startDate)) {//tmpDate.isAfter(startDate)) {
            --numIntervals;

            final TDateInterval multiInterval = new TDateInterval(dateInterval.prd *
                    numIntervals, dateInterval.periodType, 0);
            final LocalDate couponDate = dtFwdAny(endDate, multiInterval, holidayCalendar, badDayConvention, lastDay);
            tmpDate = couponDate;
            couponDates.add(couponDate);
        }

        return couponDates;

    }

    public List<LocalDate> cashFlowDates(
            final LocalDate valueDate,
            final LocalDate startDate,
            final LocalDate maturityDate,
            final TDateInterval dateInterval,
            final boolean cleanPrice,
            final TBadDayConvention badDayConvention,
            final HolidayCalendar holidayCalendar) throws Exception {

        final List<LocalDate> couponDates = couponDates(startDate,
                maturityDate,
                dateInterval,
                holidayCalendar,
                badDayConvention);
        Collections.sort(couponDates);

        final List<LocalDate> goodDates = new ArrayList();

        for (int i = 0; i < couponDates.size(); i++) {
            if (couponDates.get(i).isAfter(valueDate))
                goodDates.add(couponDates.get(i));
        }

        final LocalDate[] forwardValueDates = goodDates.toArray(new LocalDate[0]);
        final List<LocalDate> previousCouponDates = new ArrayList<>();

        for (LocalDate ld : couponDates) {
            if (le(ld, valueDate))
                previousCouponDates.add(ld);
        }

        Collections.sort(previousCouponDates);
        final LocalDate pcd = previousCouponDates.size() == 0 ? forwardValueDates[0] :
                previousCouponDates.get(previousCouponDates.size() - 1);
        final LocalDate previousDate = pcd;
        goodDates.add(previousDate);

        final Set<LocalDate> uniqueDates = new HashSet<>(goodDates);
        final List<LocalDate> uniqueDatesList = new ArrayList<>(uniqueDates);
        Collections.sort(uniqueDatesList);
        return uniqueDatesList;
    }
}
