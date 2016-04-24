# -*- coding: utf-8 -*-
"""
Created on Sat Apr  23 11:29:41 2016

@author: Santosh Bag
"""
"""


"""

import QuantLib as ql
from datetime import *

class TermStructure:

    def __init__(self, date=datetime.today()):
        self.curveDate = date


class ZerorateTermStructure(TermStructure):
    def __init__(self,zeroDates=[],zeroRates=[]):
        self.zeroDates = zeroDates
        self.zeroRates = zeroRates
    
    CURVE23 = [
        'S0023D1D Currncy',
        'S0023D1W Currncy',
        ]

    def buildZeroCurves(currencyList=[]):

