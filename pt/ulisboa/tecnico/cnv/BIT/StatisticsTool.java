package pt.ulisboa.tecnico.cnv.BIT;

import pt.ulisboa.tecnico.cnv.BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;

import java.util.concurrent.ConcurrentHashMap;
import java.lang.Thread;

public class StatisticsTool {
	static ConcurrentHashMap<String,PerThreadStats> stats = new ConcurrentHashMap<>();

	public static void doInstrumentation(String in_file, String out_file) {
			ClassInfo ci = new ClassInfo(in_file);

			// Get Routines é a chamada a metodos
			for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
				Routine routine = (Routine) e.nextElement();

				// We do not want to instrument the constructor
				if(routine.getMethodName().equals("<init>"))
					continue;

				routine.addBefore("pt/ulisboa/tecnico/cnv/BIT/StatisticsTool", "analysisInit", "null");

				InstructionArray instructions = routine.getInstructionArray();
				// Chamada a BBL's dentro de metódos
				for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
					BasicBlock bb = (BasicBlock) b.nextElement();
					bb.addBefore("pt/ulisboa/tecnico/cnv/BIT/StatisticsTool", "dynInstrCount", new Integer(bb.size()));

					Instruction instr = (Instruction) instructions.elementAt(bb.getEndAddress());
					short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];
					if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION) {
						instr.addBefore("pt/ulisboa/tecnico/cnv/BIT/StatisticsTool", "dynCheckedBranch", "null");
					}
				}

				// Contar new's and memory accesses
				for (Enumeration instrs = instructions.elements(); instrs.hasMoreElements(); ) {
					Instruction instr = (Instruction) instrs.nextElement();
					int opcode=instr.getOpcode();

					if (opcode==InstructionTable.NEW)
						instr.addBefore("pt/ulisboa/tecnico/cnv/BIT/StatisticsTool", "allocCount", new Integer(opcode));
					else if (opcode == InstructionTable.getfield)
						instr.addBefore("pt/ulisboa/tecnico/cnv/BIT/StatisticsTool", "LSFieldCount", new Integer(0));
					else if (opcode == InstructionTable.putfield)
						instr.addBefore("pt/ulisboa/tecnico/cnv/BIT/StatisticsTool", "LSFieldCount", new Integer(1));
				}
			}

			ci.write(out_file);
		}

	public static PerThreadStats getThreadStats(String threadId) {
		return stats.get(threadId);
	}

	public static void removeThreadStats(String threadId) {
		stats.remove(threadId);
	}

	public static void dynCheckedBranch(String foo) {
		String threadId = new Long(Thread.currentThread().getId()).toString();
		stats.get(threadId).branch_checks++;
	}

	// count new allocations
	public static void allocCount(int type)
	{
		String threadId = new Long(Thread.currentThread().getId()).toString();
		stats.get(threadId).newcount++;
	}

	// count memory access load & store
	public static void LSFieldCount(int type)
	{
		String threadId = new Long(Thread.currentThread().getId()).toString();

		if (type == 0)
			stats.get(threadId).fieldloadcount++;
		else
			stats.get(threadId).fieldstorecount++;
	}


	// Conta instruções e BBL's
    public static void dynInstrCount(int sizeIncr) {
		String threadId = new Long(Thread.currentThread().getId()).toString();

		stats.get(threadId).dyn_instr_count += sizeIncr;
		stats.get(threadId).dyn_bb_count++;
	}

    public static void analysisInit(String foo)
		{
			// putIfAbsent - used because instrumented functions can call for the same thread other functions and lose data

			String threadId = new Long(Thread.currentThread().getId()).toString();
			stats.putIfAbsent(threadId, new PerThreadStats());
		}
	
	public static void printUsage() {
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
