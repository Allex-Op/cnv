echo "compiling PerThreadStats"
javac pt/ulisboa/tecnico/cnv/BIT/PerThreadStats.java
echo "compiling StatisticsTool"
javac pt/ulisboa/tecnico/cnv/BIT/StatisticsTool.java
echo "compiling WebSever"
javac pt/ulisboa/tecnico/cnv/server/WebServer.java

echo "insrumentating GridScanStrategy"
java pt.ulisboa.tecnico.cnv.BIT.StatisticsTool pt/ulisboa/tecnico/cnv/solver/GridScanSolverStrategy_non_instrumented.class pt/ulisboa/tecnico/cnv/solver/GridScanSolverStrategy.class
echo "instrumentating ProgressiveScanStrategy"
java pt.ulisboa.tecnico.cnv.BIT.StatisticsTool pt/ulisboa/tecnico/cnv/solver/ProgressiveScanSolverStrategy_non_instrumented.class pt/ulisboa/tecnico/cnv/solver/ProgressiveScanSolverStrategy.class
echo "instrumentating GreedyScanStrategy"
java pt.ulisboa.tecnico.cnv.BIT.StatisticsTool pt/ulisboa/tecnico/cnv/solver/GreedyRangeScanSolverStrategy_non_instrumented.class pt/ulisboa/tecnico/cnv/solver/GreedyRangeScanSolverStrategy.class


echo "instrumentaiton complete, server ready to use"
