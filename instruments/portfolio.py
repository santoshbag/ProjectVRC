# -*- coding: utf-8 -*-
"""
Created on Fri Apr  8 05:02:07 2016

@author: sbag
"""
"""
Portfolio Class 

"""
from datetime import *

class Portfolio:
    def __init__(self,name="Portfolio1",currency="USD"):
        self.portfolio_name = name
        self.position_date = datetime.today
        self.portfolio_currency = currency
        self.position_list = []
        
    def getPortfolioDetails(self):
        portfolioDetails = {'portfolio_name': self.portfolio_name, 
        'position_date': self.position_date, 
        'portfolio_currency': self.portfolio_currency, 
        'position_list': self.position_list}
        
        return portfolioDetails
        
    def addPosition(self,position_list):
        for position in position_list:
            self.portfolio_list.append(position)
        #return self.portfolio_list.count()

    
class Position():
    def __init__(self,setupparams):
        self.product_type = setupparams['product_type']
        self.product_quantity = setupparams['product_quantity']
        self.product_price = setupparams['product_price']
        self.product_cost = setupparams['product_cost']
        self.product_currency = setupparams['product_currency']



    

        
    
    
