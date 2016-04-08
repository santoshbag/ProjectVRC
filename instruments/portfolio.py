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
        self.portfolio_list = []
        
    def getPortfolioDetails(self):
        portfolioDetails = {'portfolio_name': self.portfolio_name, 
        'position_date': self.position_date, 
        'portfolio_currency': self.portfolio_currency, 
        'portfolio_list': self.portfolio_list}
        
        return portfolioDetails
        
    def add(self,singleportfolio):
        self.portfolio_list.append(singleportfolio)
        #return self.portfolio_list.count()

class SinglePortfolio(Portfolio):
    def setPortfolio(self,setupparams):
        self.product_type = setupparams['product_type']
        self.product_quantity = setupparams['product_quantity']
        self.product_price = setupparams['product_price']
        self.product_cost = setupparams['product_cost']


if __name__ == '__main__':
    CS = SinglePortfolio('Credit Suisse CDS')
    DB = SinglePortfolio('Deutsche Bank FI','EUR')
    GS = SinglePortfolio('Goldman MBS')
    
    
    
    master = Portfolio('CS')
    master.add(CS)
    master.add(DB)
    master.add(GS)
    
    #print(CS.portfolio_name,CS.portfolio_currency)
    #det = {}    
    #det = CS.getPortfolioDetails()
    
    print(master.getPortfolioDetails())
    for plist in master.getPortfolioDetails()['portfolio_list']:
        print(plist.getPortfolioDetails())
    

        
    
    
