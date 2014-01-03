nCtrl=$1
pSize=$2
nJobPerCtrl=$3
wbase=$4
wtBase=$5

nNode=$(($nCtrl*$pSize))
wFile=$wbase
wFile+=workload.$nCtrl.$pSize.$nJobPerCtrl
echo $wFile
wTFile=$wtBase
wTFile+=waittime
echo $wTFile

echo -e "simulation.endtime 10^15\nsimulation.logtime 10^15\nsimulation.experiments 1\n\nnetwork.size $nNode\n\nprotocol.tr UniformRandomTransport\n{\n\tmindelay 7912\n\tmaxdelay 7912\n}\n\nprotocol.peer PeerProtocol\n{\n\ttransport tr\n\tpartSize $pSize\n\tsleepLength 1000\n\tcallbackInterval 1000\n\tcallbackNumTry 3\n\tmaxNumTry 5\n}\n\ninit.create NetInit\n{\n\tprotocol peer\n\tpartSize $pSize\n\tnetSpeed 1000000000\n\tlatency 100\n\tsendOverhead 50\n\trecvOverhead 50\n\tjobProcTime 1000\n\tkvsProcTime 1000\n\tworkloadFile $wFile\n}\n\ncontrol.start TrafficGene\n{\n\tprotocol peer\n\twaitTimeFile $wTFile\n\tstep simulation.endtime\n}" > config
