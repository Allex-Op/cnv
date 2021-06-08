package pt.ulisboa.tecnico.cnv.BIT;

// Object to gather stats per request
public class PerThreadStats {
    public long dyn_bb_count = 0;		// Numero de bbl's executados, kinda unecessary, mas como temos que fazer de qualquer maneira a operação, why not
    public long branch_checks = 0;		// IF's impedem pipelining e fazem o custo de execução maior
    public long dyn_instr_count = 0;		// O numero de instruções por bloco dinâmico executado

    public long newcount = 0;            // allocation of memory for new objects
    public long fieldloadcount = 0;      // load from memory
    public long fieldstorecount = 0;     // store in memory
}