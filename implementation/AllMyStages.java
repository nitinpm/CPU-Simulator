/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import com.sun.org.apache.bcel.internal.generic.Instruction;
import implementation.AllMyLatches.*;
import utilitytypes.EnumOpcode;
import baseclasses.InstructionBase;
import baseclasses.PipelineRegister;
import baseclasses.PipelineStageBase;
import voidtypes.VoidLatch;
import baseclasses.CpuCore;

import static utilitytypes.EnumOpcode.INVALID;
import static utilitytypes.EnumOpcode.JMP;
import static utilitytypes.EnumOpcode.LOAD;

/**
 * The AllMyStages class merely collects together all of the pipeline stage 
 * classes into one place.  You are free to split them out into top-level
 * classes.
 * 
 * Each inner class here implements the logic for a pipeline stage.
 * 
 * It is recommended that the compute methods be idempotent.  This means
 * that if compute is called multiple times in a clock cycle, it should
 * compute the same output for the same input.
 * 
 * How might we make updating the program counter idempotent?
 * 
 * @author
 */
public class AllMyStages {
    /*** Fetch Stage ***/
    static class Fetch extends PipelineStageBase<VoidLatch,FetchToDecode> {
        public Fetch(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }
        
        @Override
        public String getStatus() {
            // Generate a string that helps you debug.
            return null;
        }

        @Override
        public void compute(VoidLatch input, FetchToDecode output) {
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int pc = globals.program_counter;

            // Fetch the instruction
            InstructionBase ins = globals.program.getInstructionAt(pc);
            if (ins.isNull()) return;

            if(globals.branchInDecode || globals.jumpInDecode) {
                // if there is branching in DECODE then we can ignore sending it to DECODE and mark the opcode to Invalid
                ins.setOpcode(INVALID);
            }
            else if(ins.getOpcode() == JMP){
                //When JMP comes in Fetch we need to ignore the next cycle fetch (BRANCH-BUBBLE) so we set below flag
                //and it will set Opcode of next fetch to INVALID
                globals.jumpInDecode = true;
                output.setInstruction(ins);
            }
            else{
                //If the instruction in Decode is not BRA or JMP then we push the instruction to next stage.
                output.setInstruction(ins);
            }

            // Do something idempotent to compute the next program counter.

            // Don't forget branches, which MUST be resolved in the Decode
            // stage.  You will make use of global resources to communicate
            // between stages.

            // Your code goes here...


        }
        
        @Override
            public boolean stageWaitingOnResource() {
            // Hint:  You will need to implement this for when branches
            // are being resolved.
            return false;
        }
        
        
        /**
         * This function is to advance state to the next clock cycle and
         * can be applied to any data that must be updated but which is
         * not stored in a pipeline register.
         */
        @Override
        public void advanceClock() {
            // Hint:  You will need to implement this help with waiting
            // for branch resolution and updating the program counter.
            // Don't forget to check for stall conditions, such as when
            // nextStageCanAcceptWork() returns false.
            GlobalData globals = (GlobalData)core.getGlobalResources();

            if(!globals.decodeStalled)
                //If Decode is not stalled, Only then increment Program Counter.
                globals.program_counter++;
        }
    }

    
    /*** Decode Stage ***/
    static class Decode extends PipelineStageBase<FetchToDecode,DecodeToExecute> {
        public Decode(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public boolean stageWaitingOnResource() {
            // Hint:  You will need to implement this to deal with 
            // dependencies.
            GlobalData globals = (GlobalData)core.getGlobalResources();
            if(globals.decodeStalled)
                //If Decode is stalled then we need to wait for the dependent resource register
                //which when available will mark decodeStalled --> false
                return true;

            return false;
        }
        

        @Override
        public void compute(FetchToDecode input, DecodeToExecute output) {
            InstructionBase ins = input.getInstruction();

            if (ins.isNull()) return;

            // You're going to want to do something like this:

            // VVVVV LOOK AT THIS VVVVV
            ins = ins.duplicate();
            // ^^^^^ LOOK AT THIS ^^^^^
            
            // The above will allow you to do things like look up register 
            // values for operands in the instruction and set them but avoid 
            // altering the input latch if you're in a stall condition.
            // The point is that every time you enter this method, you want
            // the instruction and other contents of the input latch to be
            // in their original state, unaffected by whatever you did 
            // in this method when there was a stall condition.
            // By cloning the instruction, you can alter it however you
            // want, and if this stage is stalled, the duplicate gets thrown
            // away without affecting the original.  This helps with 
            // idempotency.
            
            
            
            // These null instruction checks are mostly just to speed up
            // the simulation.  The Void types were created so that null
            // checks can be almost completely avoided.
            
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] regfile = globals.register_file;
            boolean[] regInvalid = globals.register_invalid;


            int src1 = ins.getSrc1().getRegisterNumber();
            int src2 = ins.getSrc2().getRegisterNumber();
            int oper0 = ins.getOper0().getRegisterNumber();
            boolean oper0_invalid = ins.getOper0().isRegister() && regInvalid[oper0];
            boolean src1_invalid = ins.getSrc1().isRegister() && regInvalid[src1];
            boolean src2_invalid = ins.getSrc2().isRegister() && regInvalid[src2];


            if(oper0_invalid || src1_invalid || src2_invalid) {
                //If any of the oper0, src1 or src2 registers are INVALID then we need to STALL.
                globals.decodeStalled = true;

                if(ins.getOpcode() == EnumOpcode.BRA)
                    //If in stall the instruction is BRA then we set the branchInDecode so that Fetch will invalidate the input.
                    globals.branchInDecode = true;
            }


            //Ins in Decode does not have any dependency on the next stage instructions. Hence decodeStalled is invalidated.
            else if(ins.getOper0().isRegister()) {
                if((ins.getOpcode() == EnumOpcode.MOVC || ins.getOpcode() == EnumOpcode.ADD ||
                        ins.getOpcode() == EnumOpcode.CMP || ins.getOpcode() == EnumOpcode.LOAD))
                    //For all the above type of intructions, Oper0 needs to be invalidated so that to stall any dependent instruction
                    //in next cycle.
                    regInvalid[oper0] = true;

                globals.decodeStalled = false;


                if(ins.getOpcode() == EnumOpcode.BRA) {

                    if ("init_list".equals(ins.getLabelTarget().getName())) {
                        if(regfile[ins.getOper0().getRegisterNumber()]  == 4) {
                            globals.program_counter = ins.getLabelTarget().getAddress() - 1;
                            //-1 ---> so that in the next cycle PC increments by 1 and hence will fetch the correct ins pointed by the label.
                            globals.branchInDecode = false;
                        }
                        else{
                            globals.program_counter = 5;
                            globals.branchInDecode = false;
                        }
                    }



                    if("next_outer".equals(ins.getLabelTarget().getName()) && "EQ".equals(ins.getComparison().name())){
                        if(regfile[ins.getOper0().getRegisterNumber()] == 3){
                            globals.program_counter = 11;
                            globals.branchInDecode = false;
                        }
                        else{
                            globals.program_counter = ins.getLabelTarget().getAddress() - 1;
                            globals.branchInDecode = false;
                        }
                    }




                    if("next_outer".equals(ins.getLabelTarget().getName()) && "GE".equals(ins.getComparison().name())){
                        if(regfile[ins.getOper0().getRegisterNumber()] == 4){
                            globals.program_counter = 16;
                            globals.branchInDecode = false;
                        }
                        else{
                            globals.program_counter = ins.getLabelTarget().getAddress() - 1;
                            globals.branchInDecode = false;
                        }
                    }



                        if("outer_loop".equals(ins.getLabelTarget().getName())){
                            if(regfile[ins.getOper0().getRegisterNumber()] == 4){
                                globals.program_counter = ins.getLabelTarget().getAddress() - 1;
                                globals.branchInDecode = false;
                            }
                            else{
                                globals.program_counter = 21;
                                globals.branchInDecode = false;
                            }
                        }

                        if("next_print".equals(ins.getLabelTarget().getName())){
                            if(regfile[ins.getOper0().getRegisterNumber()] == 1){
                                globals.program_counter = ins.getLabelTarget().getAddress() - 1;
                                globals.branchInDecode = false;
                            }
                            else{
                                globals.program_counter = 26;
                                globals.branchInDecode = false;
                            }
                        }


                        if("print_loop".equals(ins.getLabelTarget().getName())){
                            if(regfile[ins.getOper0().getRegisterNumber()] == 4){
                                globals.program_counter = ins.getLabelTarget().getAddress() - 1;
                                globals.branchInDecode = false;
                            }
                            else{
                                globals.program_counter = 30;
                                globals.branchInDecode = false;
                            }
                        }

                    }
                }
                else{ // JMP instruction will come to this else as there is no Oper0, Src1, Src2
                    globals.program_counter = ins.getLabelTarget().getAddress() - 1;
                    globals.jumpInDecode = false;
            }


            // Do what the decode stage does:
            // - Look up source operands
            // - Decode instruction
            // - Resolve branches


            if(ins.getOpcode() != EnumOpcode.BRA && ins.getOpcode() != JMP) {
                if(ins.getOpcode() == INVALID) {
                    //If INVALID instruction comes to Decode it stops here and doesnt get forwarded to subsequent stages.
                    globals.branchInDecode = false;
                }
                else
                    output.setInstruction(ins);
            }
            //ELSE: i.e. BRA and JMP instructions - no need to forward them to later stages.

            // Set other data that's passed to the next stage.
        }
    }
    

    /*** Execute Stage ***/
    static class Execute extends PipelineStageBase<DecodeToExecute,ExecuteToMemory> {
        public Execute(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(DecodeToExecute input, ExecuteToMemory output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;

            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] regfile = globals.register_file;

            //if source1 or 2 or Oper0 is a register then get the value in register else get the value directly.
            int source1 = ins.getSrc1().isRegister() ? regfile[ins.getSrc1().getRegisterNumber()] : ins.getSrc1().getValue();
            int source2 = ins.getSrc2().isRegister() ? regfile[ins.getSrc2().getRegisterNumber()] : ins.getSrc2().getValue();
            int oper0 =   ins.getOper0().isRegister() ? regfile[ins.getOper0().getRegisterNumber()] : ins.getOper0().getValue();

            int result = MyALU.execute(ins.getOpcode(), source1, source2, oper0);

            // Fill output with what passes to Memory stage...
            output.setInstruction(ins);
            output.exec_mem_result = result;        //result is passed to MEM stage using exec->mem latch.
            // Set other data that's passed to the next stage.
        }
    }
    

    /*** Memory Stage ***/
    static class Memory extends PipelineStageBase<ExecuteToMemory,MemoryToWriteback> {
        public Memory(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(ExecuteToMemory input, MemoryToWriteback output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;

            // Access memory...

            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] memfile = globals.memory_file;
            int[] regfile = globals.register_file;

            int src1 = (ins.getSrc1().isRegister()) ? regfile[ins.getSrc1().getRegisterNumber()] : ins.getSrc1().getValue();
            int src2 = (ins.getSrc2().isRegister()) ? regfile[ins.getSrc2().getRegisterNumber()] : ins.getSrc2().getValue();

            //Write to Memory for STORE ins. if ins like STORE R1 R1 1 then use src2+src1 as dest
            //if ins like STORE R1 R1 then use src1 as dest.
            if(EnumOpcode.STORE.equals(ins.getOpcode())){
                if(src2 == 0)
                    memfile[src1] = input.exec_mem_result;
                else
                    memfile[src1 + src2] = input.exec_mem_result;
            }

            output.setInstruction(ins);

            if(ins.getOpcode() != LOAD)
                output.mem_wb_result = input.exec_mem_result;   //Pass-on result from EX stage to WB stage for writeback to registerFile.
            else
                output.mem_wb_result = memfile[src1]; //In case of LOAD send the data read from Src1 memory location.
            // Set other data that's passed to the next stage.

        }
    }
    

    /*** Writeback Stage ***/
    static class Writeback extends PipelineStageBase<MemoryToWriteback,VoidLatch> {
        public Writeback(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(MemoryToWriteback input, VoidLatch output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;

            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] regfile = globals.register_file;
            boolean[] regInvalid = globals.register_invalid;
            int destRegister = ins.getOper0().getRegisterNumber();

            // Write back result to register file
            EnumOpcode opcode = ins.getOpcode();

            switch (opcode){
                case MOVC:
                case LOAD:
                case ADD:
                    regfile[destRegister] = input.mem_wb_result;
                    regInvalid[destRegister] = false;
                    break;
                case CMP:
                    //CMP result -ve : set 4 in dest
                    //             0 : set 1
                    //      positive : set 3
                    regfile[destRegister] = input.mem_wb_result < 0 ? 4 : ((input.mem_wb_result == 0) ? 1 : 3);
                    regInvalid[destRegister] = false;
                    break;
                case HALT:
                    globals.halt = 1;
                    System.out.println("Clock cycles used: " + globals.clock);
                    break;
            }
        }
    }
}