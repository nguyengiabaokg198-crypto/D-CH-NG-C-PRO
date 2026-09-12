package com.example.decompiler

import com.example.dex.DexInstruction

data class BasicBlock(
    val id: Int,
    val startOffset: Int,
    val endOffset: Int,
    val instructions: List<DexInstruction>,
    val predecessors: MutableList<Int> = mutableListOf(),
    val successors: MutableList<Int> = mutableListOf()
)

class ControlFlowGraphBuilder {

    fun build(instructions: List<DexInstruction>): List<BasicBlock> {
        if (instructions.isEmpty()) return emptyList()

        // 1. Identify leaders (start of basic blocks)
        val leaders = mutableSetOf<Int>()
        leaders.add(instructions.first().offset)

        for (i in instructions.indices) {
            val ins = instructions[i]
            if (ins.targetOffset != null) {
                leaders.add(ins.targetOffset)
                if (i + 1 < instructions.size) {
                    leaders.add(instructions[i + 1].offset)
                }
            } else if (ins.opcodeName.startsWith("return") || ins.opcodeName == "throw" || ins.opcodeName.startsWith("goto")) {
                if (i + 1 < instructions.size) {
                    leaders.add(instructions[i + 1].offset)
                }
            }
        }

        // 2. Partition instructions into blocks
        val blocks = mutableListOf<BasicBlock>()
        var currentBlockInstrs = mutableListOf<DexInstruction>()
        var blockId = 0

        for (i in instructions.indices) {
            val ins = instructions[i]
            if (ins.offset in leaders && currentBlockInstrs.isNotEmpty()) {
                val start = currentBlockInstrs.first().offset
                val end = currentBlockInstrs.last().offset
                blocks.add(BasicBlock(blockId++, start, end, currentBlockInstrs.toList()))
                currentBlockInstrs.clear()
            }
            currentBlockInstrs.add(ins)
        }

        if (currentBlockInstrs.isNotEmpty()) {
            val start = currentBlockInstrs.first().offset
            val end = currentBlockInstrs.last().offset
            blocks.add(BasicBlock(blockId++, start, end, currentBlockInstrs.toList()))
        }

        // 3. Connect successors and predecessors
        val offsetToBlockId = mutableMapOf<Int, Int>()
        for (b in blocks) {
            offsetToBlockId[b.startOffset] = b.id
        }

        for (i in blocks.indices) {
            val b = blocks[i]
            val lastIns = b.instructions.last()

            if (lastIns.targetOffset != null) {
                val targetBlockId = offsetToBlockId[lastIns.targetOffset]
                if (targetBlockId != null) {
                    b.successors.add(targetBlockId)
                    blocks[targetBlockId].predecessors.add(b.id)
                }
            }

            // Fallthrough if not unconditional jump or return
            val isUnconditional = lastIns.opcodeName.startsWith("goto") ||
                    lastIns.opcodeName.startsWith("return") ||
                    lastIns.opcodeName == "throw"

            if (!isUnconditional && i + 1 < blocks.size) {
                val nextBlock = blocks[i + 1]
                b.successors.add(nextBlock.id)
                nextBlock.predecessors.add(b.id)
            }
        }

        return blocks
    }
}
