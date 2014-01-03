nCtrl=$1
pSize=$2
nJobsPerCtrl=$3
wBase=$4
wtBase=$5
srcBase=$6

echo $nCtrl
echo $pSize
echo $nJobsPerCtrl
echo $wBase
echo $wtBase
echo $srcBase

bash genWorkload.sh $nCtrl $pSize $nJobsPerCtrl $wBase
bash genConfig.sh $nCtrl $pSize $nJobsPerCtrl $wBase $wtBase

java -Xms4000m -Xmx4000m -cp peersim-1.0.5.jar:djep-1.0.0.jar:jep-2.3.0.jar:peersim-doclet.jar:$srcBase peersim.Simulator config
