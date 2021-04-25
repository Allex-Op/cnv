import BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class StatisticsTool 
{
	private static int dyn_bb_count = 0;		// Numero de bbl's executados, kinda unecessary, mas como temos que fazer de qualquer maneira a operação, why not
	private static int dyn_method_count = 0;	// Numero de metódos chamados, kinda unecessary 
	private static int branch_checks = 0;		// IF's impedem pipelining e fazem o custo de execução maior
	private static int dyn_instr_count = 0;		// O numero de instruções por bloco dinâmico executado
		
	public static void doInstrumentation(String in_file, String out_file) 
		{
			ClassInfo ci = new ClassInfo(in_file);

			// Get Routines é a chamada a metodos
			for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
				Routine routine = (Routine) e.nextElement();

				// We do not want to instrument the constructor
				if(routine.getMethodName().equals("<init>"))
					continue;

				routine.addBefore("StatisticsTool", "dynMethodCount", new Integer(1));

				InstructionArray instructions = routine.getInstructionArray();
				// Chamada a BBL's dentro de metódos
				for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
					BasicBlock bb = (BasicBlock) b.nextElement();
					bb.addBefore("StatisticsTool", "dynInstrCount", new Integer(bb.size()));

					Instruction instr = (Instruction) instructions.elementAt(bb.getEndAddress());
					short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
					if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION) {
						instr.addBefore("StatisticsTool", "dynCheckedBranch", "null");
					}
				}

				String name_output_file = ci.getSourceFileName()+"_"+routine.getMethodName();
				routine.addAfter("StatisticsTool", "printDynamic", name_output_file);
			}

			//ci.addAfter("StatisticsTool", "printDynamic", "null");
			ci.write(out_file);
		}
	
    public static synchronized void printDynamic(String foo) 
		{
			if(!foo.contains("solve"))
				return;

			try {
				System.out.println("Writing stats...");

				BufferedWriter out = new BufferedWriter(new FileWriter("/home/vagrant/cnv/CNV/"+foo+"_analysis.txt"));				

				out.write("Dynamic information summary:");
				out.write("\nNumber of methods:      " + dyn_method_count);
				out.write("\nNumber of basic blocks: " + dyn_bb_count);
				out.write("\nNumber of executed instructions: " + dyn_instr_count);
				out.write("\nTotal number of branches to check:" + branch_checks);
				out.write("\n");
				out.close();
			} catch (Exception e) {
				System.out.println("An error occurred.");
				e.printStackTrace();
			}
		}
    

	public static synchronized void dynCheckedBranch(String foo) {
		branch_checks++;
	}

	// Conta instruções e BBL's
    public static synchronized void dynInstrCount(int incr) 
		{
			dyn_instr_count += incr;
			dyn_bb_count++;
		}

    public static synchronized void dynMethodCount(int incr) 
		{
			dyn_method_count++;
		}
	
	public static void printUsage() 
	{
		System.out.println("Syntax: java StatisticsTool in_file_path [out_file_path]");
		System.exit(-1);
	}

	public static void main(String argv[]) {
		if (argv.length < 2) {
			printUsage();
		}

		try {
			String in_file = new String(argv[0]);
			String out_file = new String(argv[1]);

			doInstrumentation(in_file, out_file);
		}
		catch (NullPointerException e) {
			printUsage();
		}
	}
}
