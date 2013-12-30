# compile the java code
1. First go to the "PeerSim" directory
2. javac -cp peersim-1.0.5.jar:djep-1.0.0.jar:jep-2.3.0.jar:peersim-doclet.jar directory_where_you_put_all_your_java_program_files/*.java

# run the simulator
1. First go to the "PeerSim" directory
2. java -cp peersim-1.0.5.jar:djep-1.0.0.jar:jep-2.3.0.jar:peersim-doclet.jar:directory_where_you_put_all_your_java_program_files peersim.Simulator directory_where_you_put_your_configuration_file/configFile
