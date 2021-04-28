package pt.ulisboa.tecnico.cnv.BIT;

// Object to gather stats per request
public class PerThreadStats {
    public int dyn_bb_count = 0;		// Numero de bbl's executados, kinda unecessary, mas como temos que fazer de qualquer maneira a operação, why not
    public int branch_checks = 0;		// IF's impedem pipelining e fazem o custo de execução maior
    public int dyn_instr_count = 0;		// O numero de instruções por bloco dinâmico executado

    public int newcount = 0;            // allocation of memory for new objects
    public int fieldloadcount = 0;      // load from memory
    public int fieldstorecount = 0;     // store in memory
}