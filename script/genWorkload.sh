nCtrl=$1
pSize=$2
nJobPerCtrl=$3
wBase=$4

echo $nCtrl
echo $pSize
echo $nJobPerCtrl
echo $wBase

nAllJob=$(($nCtrl*$nJobPerCtrl))

wFile=$wBase
wFile+=workload.$nCtrl.$pSize.$nJobPerCtrl

echo "wFile is:$wFile"

nNode=$(($nCtrl*$pSize))

rm -rf $wFile

for i in `seq 1 $nAllJob` 
do
	num=$RANDOM
	num=$(($num%$nNode))
	num=$(($num/2))
	num=$(($num+1))
	echo "srun -N$num /bin/ sleep 0" >> $wFile	    
done
