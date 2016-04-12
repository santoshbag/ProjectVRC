package com.apollo.analytics.isdamodel.cds.domain;

import com.apollo.analytics.common.datetime.holidays.HolidayCalendar;
import com.apollo.analytics.common.domain.TBadDayConvention;
import com.apollo.analytics.common.utils.*;
import org.apache.log4j.Logger;

import java.time.LocalDate;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

import static com.apollo.analytics.isdamodel.utils.CdsFunctions.MIN;

/**
 * Created by aanand on 4/28/2015.
 */

public class TDateFunctions {

    private final static Logger logger = Logger.getLogger(TDateFunctions.class);

    private static int leapDays[] = {
            0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    /* JAN  FEB  MAR  APR  MAY  JUN  JUL  AUG  SEP  OCT  NOV  DEC */
    private static int days[] = {
            0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    /* JAN  FEB  MAR  APR  MAY  JUN  JUL  AUG  SEP  OCT  NOV  DEC */

    public static ReturnStatus cdsDayCountFraction(LocalDate date1, LocalDate date2, DayCount method, DoubleHolder result) {
        LocalDate currentDate;
        LocalDate temp;

        double sign = 1.;

        BooleanHolder isLeap = new BooleanHolder(false);

        if (method.equals(DayCount.ACT_365F)) {
            //result.set(date1.until(date2).getDays() / 365.);
            result.set(ChronoUnit.DAYS.between(date1, date2) / 365.);
            return ReturnStatus.SUCCESS;
        } else if (method.equals(DayCount.ACT_360)) {
            //result.set(date1.until(date2).getDays() / 360.);
            result.set(ChronoUnit.DAYS.between(date1, date2) / 360.);
            return ReturnStatus.SUCCESS;
        }

        //Check if same date
        if (date1.isEqual(date2)) {
            result.set(0);
            return ReturnStatus.SUCCESS;
        }

        if (date1.isAfter(date2)) {
            sign = -1.0;

            //reverse order
            temp = date1;
            date1 = date2;
            date2 = temp;

        }

        if (method.equals(DayCount.CDS_EFFECTIVE_RATE)) {
            /**
             * Effective rates have a year fraction of 1.0 or -1.0, depending
             * on the order of the dates(note: if the dates are the same,
             * the year fraction is 0 (handled above)
             */
            result.set(sign);
            return ReturnStatus.SUCCESS;
        }

        LongHolder actDays = new LongHolder();
        if (cdsDaysDiff(date1, date2, method, actDays).equals(ReturnStatus.FAILURE)) {
            logger.error("TDateFunctions.cdsDayCountFraction()::Error creating day diff");
            return ReturnStatus.FAILURE;
        }

        int value = method.value();
        /*
        public static final DayCount ACT_365 = new DayCount("ACT_365", ONE);
        public static final DayCount ACT_365F = new DayCount("ACT_365F", TWO);
        public static final DayCount ACT_360 = new DayCount("ACT_360", THREE);
        public static final DayCount B30_360 = new DayCount("B30_360", FOUR);
        public static final DayCount B30E_360 = new DayCount("B30E_360", FIVE);
        public static final DayCount ACT_ACT = new DayCount("ACT_ACT", SIX);
        public static final DayCount CDS_EFFECTIVE_RATE = new DayCount("CDS_EFFECTIVE_RATE", EIGHT);
        public static final DayCount CDS_DCC_LAST = new DayCount("CDS_DCC_LAST", NINE);
        public static final DayCount NONE = new DayCount("NONE", TEN);
         */
        switch (value) {
            case DayCount.FOUR: //B30_360:
            case DayCount.FIVE: //B30E_360:
            case DayCount.THREE: //ACT_360:
                result.set(actDays.get() / 360.);
                break;
            case DayCount.TWO: //ACT_365F:
                result.set(actDays.get() / 365.);
                break;
            case DayCount.ONE: //ACT_365:
            case DayCount.SIX: { //ACT_ACT: {
                /**
                 * weighted average of leap days / 366 + weighted average of non-leap days / 365
                 *
                 */

                int leapDays = 0;
                int nonLeapDays = 0;

                // handle first year
                LongHolder daysLeft = new LongHolder();
                if (cdsDaysLeftThisYear(date1, method, daysLeft).equals(ReturnStatus.FAILURE)) {
                    logger.error("TDateFunctions.cdsDayCountFraction()::Error in cdsDaysLeftThisYear");
                    return ReturnStatus.FAILURE;
                }

                if (cdsIsLeap(date1, isLeap).equals(ReturnStatus.FAILURE)) {
                    logger.error("TDateFunctions.cdsDayCountFraction()::Error in cdsIsLeap");
                    return ReturnStatus.FAILURE;
                }

                if (isLeap.get())
                    leapDays += MIN(actDays.get(), daysLeft.get());
                else
                    nonLeapDays += MIN(actDays.get(), daysLeft.get());

                /**
                 * loop through the years
                 */
                int startYear = date1.getYear();
                int endYear = date2.getYear();

                currentDate = date1;

                //loop through full years
                for (int i = startYear + 1; i < endYear; i++) {
                    //check if the previous year is leap
                    if (isLeap.get()) {
                        currentDate.plusDays(366);
                    } else
                        currentDate.plusDays(365);

                    //check if new year is leap
                    if (cdsIsLeap(currentDate, isLeap).equals(ReturnStatus.FAILURE)) {
                        logger.error("TDateFunctions.cdsDayCountFraction()::Error in cdsIsLeap");
                        return ReturnStatus.FAILURE;
                    }

                    if (isLeap.get())
                        leapDays += 366;
                    else
                        nonLeapDays += 365;
                }

                //handle last year
                if (startYear != endYear) {
                    if (cdsIsLeap(date2, isLeap).equals(ReturnStatus.FAILURE)) {
                        logger.error("TDateFunctions.cdsDayCountFraction()::Error in cdsIsLeap");
                        return ReturnStatus.FAILURE;
                    }

                    DateHolder holder = new DateHolder();
                    if (cdsYearStart(date2, holder).equals(ReturnStatus.FAILURE)) {
                        logger.error("Error in yearStart");
                        return ReturnStatus.FAILURE;
                    }

                    currentDate = holder.get();
                    daysLeft = new LongHolder();
                    if (cdsDaysDiff(currentDate, date2, method, daysLeft).equals(ReturnStatus.FAILURE)) {
                        logger.error("TDateFunctions.cdsDayCountFraction()::Error in cdsDaysDiff");
                        return ReturnStatus.FAILURE;
                    }

                    if (isLeap.get()) {
                        leapDays += daysLeft.get();
                    } else {
                        nonLeapDays += daysLeft.get();
                    }
                }

                //calculate final day count fraction
                //ISDA interpretation

                result.set(leapDays / 366. + nonLeapDays / 365.);
            }
            break;
            default:
                logger.error("TDateFunctions.cdsDayCountFraction()::Invalid method");
                return ReturnStatus.FAILURE;
        }

        double retValue = result.get() * sign;
        result.set(retValue);
        return ReturnStatus.SUCCESS;
    }

    private static ReturnStatus cdsYearStart(LocalDate date, DateHolder result) {
        LocalDate retDate = LocalDate.of(date.getYear(), 1, 1);
        result.set(retDate);

        return ReturnStatus.SUCCESS;
    }

    private static ReturnStatus cdsIsLeap(LocalDate date1, BooleanHolder isLeap) {
        // Assume not a leap year
        isLeap.set(false);

        int year = date1.getYear();
        if (year % 4 != 0) {
            ;
        } else {
            if (year % 100 != 0)
                isLeap.set(false); // divisible by 4 but not by 100
            else if (year % 400 != 0)
                ;
            else
                isLeap.set(true);
        }
        return ReturnStatus.SUCCESS;
    }

    private static ReturnStatus cdsDaysLeftThisYear(LocalDate date, DayCount method, LongHolder daysLeft) {
        int year;

        year = date.getYear();
        TMonthDayYear next_year = new TMonthDayYear();
        next_year.year = year + 1;
        next_year.month = 1;
        next_year.day = 1;

        LocalDate nextYearDate = LocalDate.of(year + 1, 1, 1);
        return cdsDaysDiff(date, nextYearDate, method, daysLeft);

    }

    private static ReturnStatus cdsDaysDiff(LocalDate date1, LocalDate date2, DayCount method, LongHolder result) {
        int negative = 0;
        LocalDate temp;

        if (date1.isAfter(date2)) {
            negative = 1;
            // reverse order
            temp = date1;
            date1 = date2;
            date2 = temp;
        }

        /*
        public static final DayCount ACT_365 = new DayCount("ACT_365", ONE);
        public static final DayCount ACT_365F = new DayCount("ACT_365F", TWO);
        public static final DayCount ACT_360 = new DayCount("ACT_360", THREE);
        public static final DayCount B30_360 = new DayCount("B30_360", FOUR);
        public static final DayCount B30E_360 = new DayCount("B30E_360", FIVE);
        public static final DayCount ACT_ACT = new DayCount("ACT_ACT", SIX);
        public static final DayCount CDS_EFFECTIVE_RATE = new DayCount("CDS_EFFECTIVE_RATE", EIGHT);
        public static final DayCount CDS_DCC_LAST = new DayCount("CDS_DCC_LAST", NINE);
        public static final DayCount NONE = new DayCount("NONE", TEN);
         */
        int value = method.value();
        switch (value) {
            case DayCount.FOUR: { //B30_360: {
                int y1 = date1.getYear();
                int m1 = date1.getMonthValue();
                int d1 = date1.getDayOfMonth();

                int y2 = date2.getYear();
                int m2 = date2.getMonthValue();
                int d2 = date2.getDayOfMonth();

                // d1 == 31 => change D1 to 30
                if (d1 == 31)
                    d1 = 30;

                // d2 == 31 and d1 is 30 or 31 change d2 to 30
                if (d2 == 31 && d1 == 30)
                    d2 = 30;

                result.set((y2 - y1) * 360 + (m2 - m1) * 30 + (d2 - d1));
            }
            break;
            case DayCount.FIVE: { //B30E_360: {
                int y1 = date1.getYear();
                int m1 = date1.getMonthValue();
                int d1 = date1.getDayOfMonth();

                int y2 = date2.getYear();
                int m2 = date2.getMonthValue();
                int d2 = date2.getDayOfMonth();
                //D1=31 => change D1 to 30
                if (d1 == 31)
                    d1 = 30;

                //D2=31 => change D2 to 30
                if (d2 == 31)
                    d2 = 30;

                result.set((y2 - y1) * 360 + (m2 - m1) * 30 + (d2 - d1));

            }
            break;
            case DayCount.ONE: //ACT_365:
            case DayCount.TWO: //ACT_365F:
            case DayCount.THREE: //ACT_360:
                //result.set(date1.until(date2).getDays());
                result.set(ChronoUnit.DAYS.between(date1, date2));
                break;
            default:
                //result.set(date1.until(date2).getDays());
                result.set(ChronoUnit.DAYS.between(date1, date2));
                break;

        }

        if (negative == 1) {
            long retValue = result.get() * -1;
            result.set(retValue);
        }

        return ReturnStatus.SUCCESS;
    }

    public static LocalDate dateFromDateAndOffset(LocalDate oldDate,
                                                  TDateInterval tDateInterval,
                                                  int index) {
        TDateInterval compoundInterval = tDateInterval;
        compoundInterval.prd *= index;

        return dtFwdAny(oldDate, compoundInterval);
    }

    public static LocalDate dtFwdAny (
            LocalDate startDate,
            TDateInterval interval,
            HolidayCalendar holidayCalendar,
            TBadDayConvention badDayConvention,
            boolean endOfMonth
    ) throws Exception {

        final LocalDate fwdDate = dtFwdAny(startDate, interval);
        LocalDate retDate = fwdDate;
        if (endOfMonth) {
            final int year = fwdDate.getYear();
            final int month = fwdDate.getMonthValue();

            final int eomDay = fwdDate.with(TemporalAdjusters.lastDayOfMonth()).getDayOfMonth();
            retDate = LocalDate.of(year, month, eomDay);
        }

        final DateHolder retDateHolder = new DateHolder();
        final ReturnStatus returnStatus = adjustedBusinessDay(retDate,
                badDayConvention,
                holidayCalendar,
                retDateHolder);

        if (returnStatus.equals(ReturnStatus.SUCCESS))
            return retDateHolder.get();
        else
            throw new Exception("Error calculating adjusted business day");
    }

    public static LocalDate dtFwdAny
            (LocalDate startDate,      /* (I) date */
             TDateInterval interval      /* (I) dateInterval */
            ) {

        TMonthDayYear mdy = new TMonthDayYear();
        TDateInterval intval = new TDateInterval();
        LocalDate retDate;

        PeriodType periodType = interval.periodType;
        int idx = periodType.index();
        switch (idx) {
            case PeriodType.ZERO: //M:                     /* MONTHly increments */
            case PeriodType.THREE: //A:                     /* ANNUAL increments */
            case PeriodType.FOUR: //Y:                     /* YEARly increments */
            case PeriodType.FIVE: //S:                     /* SEMIANNUAL increments */
            case PeriodType.SIX: //Q:                     /* QUARTERly increments */

            /* First reduce all of these types to monthly. Note that we shouldn't
             * really need code to handle A,S & Q, since JpmcdsMakeDateInterval
             * converts all of them to 'M' anyway. This code is left here for
             * people who have set up their own TDateIntervals without calling
             * JpmcdsMakeDateInterval or one of the supplied macros. The month
             * type is checked first for efficiency reasons.
             */
                intval.flag = 0;
                intval.periodType = PeriodType.M;           /* months */

                if (periodType.equals(PeriodType.M))
                    intval.prd = interval.prd;
                else if (periodType.equals(PeriodType.Y) || interval.periodType.equals(PeriodType.A))
                    intval.prd = interval.prd * 12;
                else if (periodType.equals(PeriodType.S))
                    intval.prd = interval.prd * 6;
                else
                    intval.prd = interval.prd * 3;

                dateToMDY(startDate, mdy);
                mdy.month += intval.prd;

                normalizeMDY(mdy);
                retDate = LocalDate.of(mdy.year, mdy.month, mdy.day);

                break;

            case PeriodType.ONE: //D: /* Daily increments */
                retDate = startDate.plusDays(interval.prd);
                break;

            case PeriodType.SEVEN: //W:
                retDate = startDate.plusDays(interval.prd * 7);
                break;                          /* WEEKly increments */

            default:
                throw new RuntimeException("Unknown period type");
        }

        return retDate;
    }

    private static void normalizeMDY(TMonthDayYear mdy) {
        int month = mdy.month;            /* store in local vars for */
        int year = mdy.year;             /* ... speed */
        int day = mdy.day;

        while (month < 1) {
            month += 12;
            year--;
        }

        while (month > 12) {
            month -= 12;
            year++;
        }

        if (day < 1 || day > 31) {
            throw new RuntimeException("Invalid days in month");
        }

        Year year1 = Year.of(year);

        if (year1.isLeap()) {
            if (day > leapDays[month]) {
                day = leapDays[month];
            }
        } else {
            if (day > days[month]) {
                day = days[month];
            }
        }

        mdy.month = month;
        mdy.year = year;
        mdy.day = day;
    }

    private static void dateToMDY(LocalDate startDate, TMonthDayYear mdy) {
        mdy.year = startDate.getYear();
        mdy.month = startDate.getMonth().getValue();
        mdy.day = startDate.getDayOfMonth();
        mdy.isLeap = startDate.isLeapYear();
    }

    /*

    public static final TBadDayConvention FOLLOW = new TBadDayConvention("FOLLOW", 70);
    public static final TBadDayConvention PREVIOUS = new TBadDayConvention("PREVIOUS", 80);
    public static final TBadDayConvention NONE = new TBadDayConvention("NONE", 78);
    public static final TBadDayConvention MODIFIED = new TBadDayConvention("MODIFIED", 77);

     */
    public static ReturnStatus adjustedBusinessDay(LocalDate date, TBadDayConvention method, HolidayCalendar holidayCalendar, DateHolder returnDate) {
        if (method.equals(TBadDayConvention.NONE)) {
            returnDate.set(date);
            return ReturnStatus.SUCCESS;
        }

        try {
            LocalDate retDate = date;
            int intervalSign = 1;

            int value = method.value();
            switch (value) {
                case TBadDayConvention.SEVENTY_EIGHT: //NONE:
                    break;
                case TBadDayConvention.SEVENTY: //FOLLOW:
                    //intervalSign += 1;
                    //retDate = nextBusinessDay(date, intervalSign, holidayList);
                    retDate = holidayCalendar.getNextBusinessDay(date, intervalSign);
                    break;
                case TBadDayConvention.EIGHTY: //PREVIOUS:
                    intervalSign = -1;
                    //retDate = nextBusinessDay(date, intervalSign, holidayList);
                    retDate = holidayCalendar.getNextBusinessDay(date, intervalSign);
                    break;
                case TBadDayConvention.SEVENTY_SEVEN: //MODIFIED:
                /*
                ** Go forwards first. If you wind up in a different
                ** month, then go backwards.
                */
                    intervalSign = 1;
                    LocalDate nextDate = holidayCalendar.getNextBusinessDay(date, intervalSign);

                    while (date.getMonth().getValue() != nextDate.getMonth().getValue()) {
                        //Go back
                        nextDate = nextDate.minusDays(1);
                        nextDate = holidayCalendar.getNextBusinessDay(nextDate, -intervalSign);
                    }

                    retDate = nextDate;
                    break;
                default:
                    retDate = date;
            }

            returnDate.set(retDate);
            return ReturnStatus.SUCCESS;
        } catch (Exception ex) {
            logger.error(ex);
            return ReturnStatus.FAILURE;
        }

    }

    private static LocalDate cdsDateToEOM(LocalDate input) {
        final int day = input.lengthOfMonth();
        final int month = input.getMonthValue();
        final int year = input.getYear();

        return LocalDate.of(year, month, day);
    }



    public static boolean IS_BETWEEN(LocalDate x, LocalDate a, LocalDate b) {
        return a.isBefore(b)
                ? (x.isEqual(a) || x.isAfter(a)) && (x.isEqual(b) || x.isBefore(b))
                : (x.isEqual(b) || x.isAfter(b)) && (x.isEqual(a) || x.isBefore(a));
    }
}


