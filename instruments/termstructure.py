# -*- coding: utf-8 -*-
"""
Created on Sat Apr  23 11:29:41 2016

@author: Santosh Bag
"""
"""


"""

import QuantLib as ql,csv,xlrd
import math
from datetime import *


class TermStructure:

    def __init__(self, date=datetime.today()):
        self.curveDate = ql.Date(date.day, date.month, date.year)


class ZerorateTermStructure(TermStructure):

    
    """
    The discount curve is a mapping of dates  -> discount factors for each currency (BBG curve)
    Each Bloomberg Curve (e.g S0023) acts as a key to the corresponding mapping
    """
    zeroCurveData = {}
    zeroCurveObjects = {}

    SWAP = 0
    OIS = 1
    BBGCURVE = {'S0023D':['USD',SWAP],
                'S0004':['CAD',SWAP],
                'S0021':['CHF',SWAP],
                'S0045':['EUR',SWAP],
                'S0022':['GBP',SWAP],
                'S0046':['INR',SWAP],
                'S0013':['JPY',SWAP],
                'S0042':['USD',OIS]}

    def buildZeroCurves(self):
        discfactordates=[]
        discfactors=[]

        with open('df.csv', 'r') as discount_factor_file:
            dfs = csv.reader(discount_factor_file)
            for rows in dfs:
                curve = list(rows)[0]
                zerodate = xlrd.xldate.xldate_as_datetime(float(list(rows)[1]),0)
                discount_factor = float(list(rows)[2])
                if not curve in self.zeroCurveData:
                    self.zeroCurveData[curve] = [[],[],[]]

                self.zeroCurveData[curve][0].append(ql.Date(zerodate.day,zerodate.month,zerodate.year))
                self.zeroCurveData[curve][1].append(discount_factor)

                
                # Calculate period in Act/365 convention
                period = (ql.Date(zerodate.day,zerodate.month,zerodate.year) - self.curveDate)/365
                period =ql.Thirty360().yearFraction(self.curveDate,ql.Date(zerodate.day,zerodate.month,zerodate.year))
                # Calculate the zero rate for the given date with Continous compounding frequency.
                zero_rate = -math.log(discount_factor)/period
                self.zeroCurveData[curve][2].append(zero_rate)

        #print(self.zeroCurveData[curve][0])
        # Convert discount factors to zero spot rates with 30/360 daycount and continous compounding.
        zeroDaycount = ql.Thirty360()
        zeroCalendar = ql.UnitedStates()
        zeroInterpolation = ql.Linear()
        zeroCompounding = ql.Compounded
        zeroCompoundingFreq = ql.Continuous
        
        for curve in self.zeroCurveData:
            if not curve in self.zeroCurveObjects:
                self.zeroCurveObjects[self.BBGCURVE[curve][0]] = [[],[]]
            spotCurve = ql.ZeroCurve(self.zeroCurveData[curve][0],
                                     self.zeroCurveData[curve][2],
                                     zeroDaycount,
                                     zeroCalendar,
                                     zeroInterpolation,
                                     zeroCompounding,
                                     zeroCompoundingFreq)
            self.zeroCurveObjects[self.BBGCURVE[curve][0]][self.BBGCURVE[curve][1]].append(spotCurve)



#--------------Test Code--------------------
if __name__ == '__main__':
    cdate = date(2016,4,26)
    vrcTermStructure = ZerorateTermStructure(cdate)
    vrcTermStructure.buildZeroCurves()

    curve = vrcTermStructure.zeroCurveObjects['USD'][0]

    for dates in curve.dates():
        print(dates)




