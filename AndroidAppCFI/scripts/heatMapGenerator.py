
import seaborn as sns
import matplotlib.pyplot as plt
import pandas as pd
import numpy as np
from itertools import product
from datetime import datetime

import os

root_dir = os.getcwd().replace("scripts", "").replace("tools/AndroidCFI", "")
print(root_dir)

data = pd.read_csv(root_dir+'db/processedRecords.out', sep=",", header=None)
data.columns = ["t", "m", "ad", "bound"]
#data = data[100:200]


x_max = data["t"].max()+1
y_max = data["m"].max()+1

x_min = max(data["t"].min()-1,0)
y_min = max(data["m"].min()-1,0)

print(x_max)
print(y_max)

print(x_min)
print(y_min)

dateTimeObj = datetime.now()
timestampStr = dateTimeObj.strftime("%d%b%Y%H%M%S%f")


#df = pd.DataFrame({'x': np.arange(x_max), 'y': np.arange(y_max)})
df = pd.DataFrame(list(product(np.arange(x_min, x_max), np.arange(y_min, y_max))), columns=['t', 'm'])
#df = df_full[0:100]
#print(df)
#print("\n")


df=df.merge(data[["t", "m", "ad"]],  on=['t', 'm'], how='left')
df.fillna(0, inplace=True)
#print(df)
#print("\n")

#fig, ax = plt.subplots(figsize=(60,5))
fig, ax = plt.subplots(figsize=(22,6))
#fig, ax = plt.subplots()
qp = sns.heatmap(df[['t', 'm', 'ad']].set_index(['m', 't'])['ad'].unstack(), ax=ax, cmap="Blues")
ax.invert_yaxis()

scatter_d = data.loc[data["bound"]>0,["t","m"]]
#print("scatter_d")
#print(scatter_d)
#print("\n")

print("==\n")
x_multiplier = (x_max-x_min)/x_max
print("x_multiplier")
print(x_multiplier)
print("==\n")
ax.scatter(scatter_d['t']+0.5,scatter_d['m']+0.5, color='black')
#ax.scatter(scatter_d['t']+0.5,scatter_d['m']+0.5, color='black')
#ax.scatter(data['t']*0.86,data['m']-0.5, color='black')
#ax.scatter(1.8,3.5, color='green')

ax.set_title('Heatmap and scatter points')


#mng = plt.get_current_fig_manager()
#mng.full_screen_toggle()

# generate your plot




plt.savefig(root_dir+"graphs/heatmap_"+timestampStr+"_.png",bbox_inches='tight', pad_inches=0)
plt.savefig(root_dir+"graphs/heatmap.png",bbox_inches='tight', pad_inches=0, dpi=fig.dpi)

#plt.show()

print("=====")
