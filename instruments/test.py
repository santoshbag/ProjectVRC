import csv,xlrd,datetime

if __name__ == '__main__':

    discfactordates=[]
    discdactors=[]
    dftermstruct = {}

    with open('df.csv', 'r') as usdf:
        usddfs = csv.reader(usdf)
        for rows in usddfs:
            dt = xlrd.xldate.xldate_as_datetime(float(list(rows)[1]),0)
            if not list(rows)[0] in dftermstruct:
                print('adding key',list(rows)[0],'\n')
                dftermstruct[list(rows)[0]] = [[],[]]

            #print('keys',dftermstruct[list(rows)[0]][1],'\n')
            dftermstruct[list(rows)[0]][0].append(dt.date())
            #print('adding date',dftermstruct[list(rows)[0]][0],'\n')
            dftermstruct[list(rows)[0]][1].append(float(list(rows)[2]))
            
    for keys in dftermstruct:
        print(dftermstruct[keys],'\n')
