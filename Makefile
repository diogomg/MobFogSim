run:
	/usr/lib/jvm/java-8-openjdk-amd64/bin/java -Xmx10g -Dfile.encoding=UTF-8 -classpath bin:jars/cloudsim-3.0.3-sources.jar:jars/cloudsim-3.0.3.jar:jars/cloudsim-examples-3.0.3-sources.jar:jars/cloudsim-examples-3.0.3.jar:jars/commons-math3-3.5/commons-math3-3.5.jar:jars/guava-18.0.jar:jars/json-simple-1.1.1.jar:jars/junit.jar:jars/org.hamcrest.core_1.3.0.v201303031735.jar org.fog.vmmobile.AppExemplo2 1 290538 0 0 1 11 0 0 0 61

cpaverages:
	cp -r /local1/diogomg/Documents/myifogsim/averages averages

cpbin:
	cp -r /local1/diogomg/Documents/myifogsim/bin bin

cpinput:
	cp -r /home/diogomg/Downloads/part\ 2 input

cpjar:
	cp -r /local1/diogomg/Documents/myifogsim/jars jars
	cp /home/diogomg/.p2/pool/plugins/org.junit_4.12.0.v201504281640/junit.jar /home/diogomg/.p2/pool/plugins/org.hamcrest.core_1.3.0.v201303031735.jar jars

cpoutputlatencies:
	cp -r /local1/diogomg/Documents/myifogsim/outputLatencies outputLatencies

cpsrc:
	cp -r /local1/diogomg/Documents/myifogsim/src src


cpfiles: cpjar cpbin cpaverages cpoutputlatencies cpsrc

clean:
	rm *.txt

mvfiles:
	 scp -r -P 3015 testes diogomg@eco:/local1/diogo/myifogsim
