/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import utilitytypes.IGlobals;
import tools.InstructionSequence;

/**
 * As a design choice, some data elements that are accessed by multiple
 * pipeline stages are stored in a common object.
 * 
 * TODO:  Add to this any additional global or shared state that is missing.
 * 
 * @author 
 */
public class GlobalData implements IGlobals {
    public InstructionSequence program;
    public int program_counter = 0;
    public int[] register_file = new int[32];
    public boolean[] register_invalid = new boolean[32];
    public int[] memory_file = new int[100];

    @Override
    public void reset() {
        program_counter = 0;
        register_file = new int[32];
    }
    
    
    // Other global and shared variables here....
    public int halt = 0;                   //used to indicate when to halt: set to 1 when HALT reaches WB stage.
    public boolean decodeStalled = false;  //flag to communicate to fetch when decode is stalled.
    public boolean branchInDecode = false; //flag to communicate to fetch when decode has BRA instruction.
    public boolean jumpInDecode = false;   //flag to communicate to fetch when decode has JMP instruction.
    public int clock = 0;                  //counter to count clock cyles used.
}
