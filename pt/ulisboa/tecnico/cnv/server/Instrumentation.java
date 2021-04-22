package pt.ulisboa.tecnico.cnv.server;

import BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;

public class Instrumentation 
{
	private static int dyn_method_count = 0;
	private static int dyn_bb_count = 0;
	private static int dyn_instr_count = 0;

	private static StatisticsBranch[] branch_info;
	private static int branch_number;
	private static int branch_pc;
	private static String branch_class_name;
	private static String branch_method_name;
		
	public static void printUsage() 
		{
			System.out.println("Syntax: java Instrumentation -stat_type in_path [out_path]");
			System.out.println("        where stat_type can be:");
			System.out.println("        dynamic:    dynamic properties");
			System.out.println("        branch:     gathers branch outcome statistics");
			System.out.println();
			System.out.println("        in_path:  directory from which the class files are read");
			System.out.println("        out_path: directory to which the class files are written");
			System.out.println("        Both in_path and out_path are required");
			System.out.println("        in which case only in_path is required");
			System.exit(-1);
		}

	public static void doDynamic(File in_dir, File out_dir) 
		{
			String filelist[] = in_dir.list();
			
			for (int i = 0; i < filelist.length; i++) {
				String filename = filelist[i];
				if (filename.endsWith(".class")) {
					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					ClassInfo ci = new ClassInfo(in_filename);
					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();
						routine.addBefore("Instrumentation", "dynMethodCount", new Integer(1));
                    
						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
							BasicBlock bb = (BasicBlock) b.nextElement();
							bb.addBefore("Instrumentation", "dynInstrCount", new Integer(bb.size()));
						}
					}
					ci.addAfter("Instrumentation", "printDynamic", "null");
					ci.write(out_filename);
				}
			}
		}
	
    public static synchronized void printDynamic(String foo) 
		{
			System.out.println("Dynamic information summary:");
			System.out.println("Number of methods:      " + dyn_method_count);
			System.out.println("Number of basic blocks: " + dyn_bb_count);
			System.out.println("Number of instructions: " + dyn_instr_count);
		
			if (dyn_method_count == 0) {
				return;
			}
		
			float instr_per_bb = (float) dyn_instr_count / (float) dyn_bb_count;
			float instr_per_method = (float) dyn_instr_count / (float) dyn_method_count;
			float bb_per_method = (float) dyn_bb_count / (float) dyn_method_count;
		
			System.out.println("Average number of instructions per basic block: " + instr_per_bb);
			System.out.println("Average number of instructions per method:      " + instr_per_method);
			System.out.println("Average number of basic blocks per method:      " + bb_per_method);
		}
    

    public static synchronized void dynInstrCount(int incr) 
		{
			dyn_instr_count += incr;
			dyn_bb_count++;
		}

    public static synchronized void dynMethodCount(int incr) 
		{
			dyn_method_count++;
		}

	public static void doBranch(File in_dir, File out_dir) 
		{
			String filelist[] = in_dir.list();
			int k = 0;
			int total = 0;
			
			for (int i = 0; i < filelist.length; i++) {
				String filename = filelist[i];
				if (filename.endsWith(".class")) {
					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					ClassInfo ci = new ClassInfo(in_filename);

					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();
						InstructionArray instructions = routine.getInstructionArray();
						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
							BasicBlock bb = (BasicBlock) b.nextElement();
							Instruction instr = (Instruction) instructions.elementAt(bb.getEndAddress());
							short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
							if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION) {
								total++;
							}
						}
					}
				}
			}
			
			for (int i = 0; i < filelist.length; i++) {
				String filename = filelist[i];
				if (filename.endsWith(".class")) {
					String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
					ClassInfo ci = new ClassInfo(in_filename);

					for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
						Routine routine = (Routine) e.nextElement();
						routine.addBefore("Instrumentation", "setBranchMethodName", routine.getMethodName());
						InstructionArray instructions = routine.getInstructionArray();
						for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
							BasicBlock bb = (BasicBlock) b.nextElement();
							Instruction instr = (Instruction) instructions.elementAt(bb.getEndAddress());
							short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
							if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION) {
								instr.addBefore("Instrumentation", "setBranchPC", new Integer(instr.getOffset()));
								instr.addBefore("Instrumentation", "updateBranchNumber", new Integer(k));
								instr.addBefore("Instrumentation", "updateBranchOutcome", "BranchOutcome");
								k++;
							}
						}
					}
					ci.addBefore("Instrumentation", "setBranchClassName", ci.getClassName());
					ci.addBefore("Instrumentation", "branchInit", new Integer(total));
					ci.addAfter("Instrumentation", "printBranch", "null");
					ci.write(out_filename);
				}
			}	
		}

	public static synchronized void setBranchClassName(String name)
		{
			branch_class_name = name;
		}

	public static synchronized void setBranchMethodName(String name) 
		{
			branch_method_name = name;
		}
	
	public static synchronized void setBranchPC(int pc)
		{
			branch_pc = pc;
		}
	
	public static synchronized void branchInit(int n) 
		{
			if (branch_info == null) {
				branch_info = new StatisticsBranch[n];
			}
		}

	public static synchronized void updateBranchNumber(int n)
		{
			branch_number = n;
			
			if (branch_info[branch_number] == null) {
				branch_info[branch_number] = new StatisticsBranch(branch_class_name, branch_method_name, branch_pc);
			}
		}

	public static synchronized void updateBranchOutcome(int br_outcome)
		{
			if (br_outcome == 0) {
				branch_info[branch_number].incrNotTaken();
			}
			else {
				branch_info[branch_number].incrTaken();
			}
		}

	public static synchronized void printBranch(String foo)
		{
			System.out.println("Branch summary:");
			System.out.println("CLASS NAME" + '\t' + "METHOD" + '\t' + "PC" + '\t' + "TAKEN" + '\t' + "NOT_TAKEN");
			
			for (int i = 0; i < branch_info.length; i++) {
				if (branch_info[i] != null) {
					branch_info[i].print();
				}
			}
		}
	
			
	public static void main(String argv[]) 
		{
			if (argv.length < 2 || !argv[0].startsWith("-")) {
				printUsage();
			}

			if (argv[0].equals("-dynamic")) {
				if (argv.length != 3) {
					printUsage();
				}
				
				try {
					File in_dir = new File(argv[1]);
					File out_dir = new File(argv[2]);

					if (in_dir.isDirectory() && out_dir.isDirectory()) {
						doDynamic(in_dir, out_dir);
					}
					else {
						printUsage();
					}
				}
				catch (NullPointerException e) {
					printUsage();
				}
			} else if (argv[0].equals("-branch")) {
				if (argv.length != 3) {
					printUsage();
				}
				
				try {
					File in_dir = new File(argv[1]);
					File out_dir = new File(argv[2]);

					if (in_dir.isDirectory() && out_dir.isDirectory()) {
						doBranch(in_dir, out_dir);
					}
					else {
						printUsage();
					}
				}
				catch (NullPointerException e) {
					printUsage();
				}
			}
		}
}
