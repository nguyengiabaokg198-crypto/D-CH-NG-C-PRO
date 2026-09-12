package com.example.dex

class DalvikDisassembler(
    private val strings: List<String>,
    private val types: List<String>,
    private val fields: List<DexField>,
    private val methods: List<DexMethod>
) {

    fun disassemble(insns: ShortArray): List<DexInstruction> {
        val result = mutableListOf<DexInstruction>()
        var pc = 0

        while (pc < insns.size) {
            val startPc = pc
            val word0 = insns[pc].toInt() and 0xFFFF
            val opcode = word0 and 0xFF

            // Check for Dalvik payload structures (packed-switch-payload, sparse-switch-payload, fill-array-data-payload)
            if (word0 == 0x0100) { // packed-switch-payload
                val size = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                pc += 2 + 2 + size * 2 // ident(1) + size(1) + first_key(2) + targets(2 * size)
                continue
            }
            if (word0 == 0x0200) { // sparse-switch-payload
                val size = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                pc += 2 + size * 2 + size * 2 // ident(1) + size(1) + keys(2 * size) + targets(2 * size)
                continue
            }
            if (word0 == 0x0300) { // fill-array-data-payload
                val elementWidth = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 1
                val size = if (pc + 3 < insns.size) {
                    val low = insns[pc + 2].toInt() and 0xFFFF
                    val high = insns[pc + 3].toInt() and 0xFFFF
                    (high shl 16) or low
                } else 0
                val words = (size * elementWidth + 1) / 2
                pc += 4 + words
                continue
            }

            val instr = when (opcode) {
                0x00 -> { // nop
                    pc += 1
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "nop")
                }

                0x01, 0x04, 0x07 -> { // move, move-wide, move-object
                    val vA = (word0 shr 8) and 0x0F
                    val vB = (word0 shr 12) and 0x0F
                    pc += 1
                    val name = when (opcode) { 0x04 -> "move-wide"; 0x07 -> "move-object"; else -> "move" }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB))
                }

                0x02, 0x05, 0x08 -> { // move/from16, move-wide/from16, move-object/from16
                    val vA = (word0 shr 8) and 0xFF
                    val vB = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    pc += 2
                    val name = when (opcode) { 0x05 -> "move-wide/from16"; 0x08 -> "move-object/from16"; else -> "move/from16" }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB))
                }

                0x03, 0x06, 0x09 -> { // move/16, move-wide/16, move-object/16
                    val vA = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val vB = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    pc += 3
                    val name = when (opcode) { 0x06 -> "move-wide/16"; 0x09 -> "move-object/16"; else -> "move/16" }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB))
                }

                0x0a, 0x0b, 0x0c, 0x0d -> { // move-result, -wide, -object, move-exception
                    val vA = (word0 shr 8) and 0xFF
                    pc += 1
                    val name = when (opcode) {
                        0x0b -> "move-result-wide"
                        0x0c -> "move-result-object"
                        0x0d -> "move-exception"
                        else -> "move-result"
                    }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA))
                }

                0x0e -> { // return-void
                    pc += 1
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "return-void")
                }

                0x0f, 0x10, 0x11 -> { // return, return-wide, return-object
                    val vA = (word0 shr 8) and 0xFF
                    pc += 1
                    val name = when (opcode) { 0x10 -> "return-wide"; 0x11 -> "return-object"; else -> "return" }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA))
                }

                0x12 -> { // const/4
                    val vA = (word0 shr 8) and 0x0F
                    val lit = ((word0 shr 12) shl 28) shr 28
                    pc += 1
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "const/4", registers = listOf(vA), literal = lit.toLong())
                }

                0x13 -> { // const/16
                    val vA = (word0 shr 8) and 0xFF
                    val lit = if (pc + 1 < insns.size) insns[pc + 1].toLong() else 0L
                    pc += 2
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "const/16", registers = listOf(vA), literal = lit)
                }

                0x14 -> { // const
                    val vA = (word0 shr 8) and 0xFF
                    val litLow = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val litHigh = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    val lit = (litHigh shl 16) or litLow
                    pc += 3
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "const", registers = listOf(vA), literal = lit.toLong())
                }

                0x15 -> { // const/high16
                    val vA = (word0 shr 8) and 0xFF
                    val high = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val lit = high.toLong() shl 16
                    pc += 2
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "const/high16", registers = listOf(vA), literal = lit)
                }

                0x16 -> { // const-wide/16
                    val vA = (word0 shr 8) and 0xFF
                    val lit = if (pc + 1 < insns.size) insns[pc + 1].toLong() else 0L
                    pc += 2
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "const-wide/16", registers = listOf(vA), literal = lit)
                }

                0x17 -> { // const-wide/32
                    val vA = (word0 shr 8) and 0xFF
                    val litLow = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val litHigh = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    val lit = ((litHigh shl 16) or litLow).toLong()
                    pc += 3
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "const-wide/32", registers = listOf(vA), literal = lit)
                }

                0x18 -> { // const-wide (64-bit)
                    val vA = (word0 shr 8) and 0xFF
                    val w1 = if (pc + 1 < insns.size) insns[pc + 1].toLong() and 0xFFFF else 0L
                    val w2 = if (pc + 2 < insns.size) insns[pc + 2].toLong() and 0xFFFF else 0L
                    val w3 = if (pc + 3 < insns.size) insns[pc + 3].toLong() and 0xFFFF else 0L
                    val w4 = if (pc + 4 < insns.size) insns[pc + 4].toLong() and 0xFFFF else 0L
                    val lit = (w4 shl 48) or (w3 shl 32) or (w2 shl 16) or w1
                    pc += 5
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "const-wide", registers = listOf(vA), literal = lit)
                }

                0x19 -> { // const-wide/high16
                    val vA = (word0 shr 8) and 0xFF
                    val high = if (pc + 1 < insns.size) insns[pc + 1].toLong() and 0xFFFF else 0L
                    val lit = high shl 48
                    pc += 2
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "const-wide/high16", registers = listOf(vA), literal = lit)
                }

                0x1a -> { // const-string
                    val vA = (word0 shr 8) and 0xFF
                    val strIdx = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    pc += 2
                    val strVal = if (strIdx in strings.indices) strings[strIdx] else "string@$strIdx"
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "const-string", registers = listOf(vA), stringVal = strVal)
                }

                0x1b -> { // const-string/jumbo
                    val vA = (word0 shr 8) and 0xFF
                    val low = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val high = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    val strIdx = (high shl 16) or low
                    pc += 3
                    val strVal = if (strIdx in strings.indices) strings[strIdx] else "string@$strIdx"
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "const-string/jumbo", registers = listOf(vA), stringVal = strVal)
                }

                0x1c -> { // const-class
                    val vA = (word0 shr 8) and 0xFF
                    val typeIdx = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    pc += 2
                    val typeVal = if (typeIdx in types.indices) types[typeIdx] else "type@$typeIdx"
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "const-class", registers = listOf(vA), targetType = typeVal)
                }

                0x1d -> { // monitor-enter
                    val vA = (word0 shr 8) and 0xFF
                    pc += 1
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "monitor-enter", registers = listOf(vA))
                }

                0x1e -> { // monitor-exit
                    val vA = (word0 shr 8) and 0xFF
                    pc += 1
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "monitor-exit", registers = listOf(vA))
                }

                0x1f, 0x22 -> { // check-cast, new-instance
                    val vA = (word0 shr 8) and 0xFF
                    val typeIdx = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    pc += 2
                    val name = if (opcode == 0x22) "new-instance" else "check-cast"
                    val typeVal = if (typeIdx in types.indices) types[typeIdx] else "type@$typeIdx"
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA), targetType = typeVal)
                }

                0x20 -> { // instance-of
                    val vA = (word0 shr 8) and 0x0F
                    val vB = (word0 shr 12) and 0x0F
                    val typeIdx = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    pc += 2
                    val typeVal = if (typeIdx in types.indices) types[typeIdx] else "type@$typeIdx"
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "instance-of", registers = listOf(vA, vB), targetType = typeVal)
                }

                0x21 -> { // array-length
                    val vA = (word0 shr 8) and 0x0F
                    val vB = (word0 shr 12) and 0x0F
                    pc += 1
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "array-length", registers = listOf(vA, vB))
                }

                0x23 -> { // new-array
                    val vA = (word0 shr 8) and 0x0F
                    val vB = (word0 shr 12) and 0x0F
                    val typeIdx = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    pc += 2
                    val typeVal = if (typeIdx in types.indices) types[typeIdx] else "type@$typeIdx"
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "new-array", registers = listOf(vA, vB), targetType = typeVal)
                }

                0x24 -> { // filled-new-array
                    val count = (word0 shr 12) and 0x0F
                    val typeIdx = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val word2 = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    val vC = (word0 shr 8) and 0x0F
                    val vD = word2 and 0x0F
                    val vE = (word2 shr 4) and 0x0F
                    val vF = (word2 shr 8) and 0x0F
                    val vG = (word2 shr 12) and 0x0F
                    val regs = listOf(vC, vD, vE, vF, vG).take(count)
                    pc += 3
                    val typeVal = if (typeIdx in types.indices) types[typeIdx] else "type@$typeIdx"
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "filled-new-array", registers = regs, targetType = typeVal)
                }

                0x25 -> { // filled-new-array/range
                    val count = (word0 shr 8) and 0xFF
                    val typeIdx = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val vC = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    val regs = (0 until count).map { vC + it }
                    pc += 3
                    val typeVal = if (typeIdx in types.indices) types[typeIdx] else "type@$typeIdx"
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "filled-new-array/range", registers = regs, targetType = typeVal)
                }

                0x26 -> { // fill-array-data
                    val vA = (word0 shr 8) and 0xFF
                    val relLow = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val relHigh = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    val relOffset = (relHigh shl 16) or relLow
                    pc += 3
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "fill-array-data", registers = listOf(vA), targetOffset = startPc + relOffset)
                }

                0x27 -> { // throw
                    val vA = (word0 shr 8) and 0xFF
                    pc += 1
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "throw", registers = listOf(vA))
                }

                0x28 -> { // goto
                    val relOffset = (word0 shr 8).toByte().toInt()
                    pc += 1
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "goto", targetOffset = startPc + relOffset)
                }

                0x29 -> { // goto/16
                    val relOffset = if (pc + 1 < insns.size) insns[pc + 1].toInt() else 0
                    pc += 2
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "goto/16", targetOffset = startPc + relOffset)
                }

                0x2a -> { // goto/32
                    val low = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val high = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    val relOffset = (high shl 16) or low
                    pc += 3
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "goto/32", targetOffset = startPc + relOffset)
                }

                0x2b -> { // packed-switch
                    val vA = (word0 shr 8) and 0xFF
                    val low = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val high = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    val relOffset = (high shl 16) or low
                    val payloadOffset = startPc + relOffset
                    pc += 3

                    // Parse packed-switch payload if within insns
                    var keys = emptyList<Int>()
                    var targets = emptyList<Int>()
                    if (payloadOffset in insns.indices && insns[payloadOffset].toInt() and 0xFFFF == 0x0100) {
                        val size = if (payloadOffset + 1 < insns.size) insns[payloadOffset + 1].toInt() and 0xFFFF else 0
                        val fLow = if (payloadOffset + 2 < insns.size) insns[payloadOffset + 2].toInt() and 0xFFFF else 0
                        val fHigh = if (payloadOffset + 3 < insns.size) insns[payloadOffset + 3].toInt() and 0xFFFF else 0
                        val firstKey = (fHigh shl 16) or fLow

                        val kList = mutableListOf<Int>()
                        val tList = mutableListOf<Int>()
                        for (i in 0 until size) {
                            val offIdx = payloadOffset + 4 + i * 2
                            if (offIdx + 1 < insns.size) {
                                val tLow = insns[offIdx].toInt() and 0xFFFF
                                val tHigh = insns[offIdx + 1].toInt() and 0xFFFF
                                val tRel = (tHigh shl 16) or tLow
                                kList.add(firstKey + i)
                                tList.add(startPc + tRel)
                            }
                        }
                        keys = kList
                        targets = tList
                    }

                    DexInstruction(
                        offset = startPc,
                        opcode = opcode,
                        opcodeName = "packed-switch",
                        registers = listOf(vA),
                        targetOffset = payloadOffset,
                        switchKeys = keys,
                        switchTargets = targets
                    )
                }

                0x2c -> { // sparse-switch
                    val vA = (word0 shr 8) and 0xFF
                    val low = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val high = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    val relOffset = (high shl 16) or low
                    val payloadOffset = startPc + relOffset
                    pc += 3

                    var keys = emptyList<Int>()
                    var targets = emptyList<Int>()
                    if (payloadOffset in insns.indices && insns[payloadOffset].toInt() and 0xFFFF == 0x0200) {
                        val size = if (payloadOffset + 1 < insns.size) insns[payloadOffset + 1].toInt() and 0xFFFF else 0
                        val kList = mutableListOf<Int>()
                        val tList = mutableListOf<Int>()
                        val keysBase = payloadOffset + 2
                        val targetsBase = keysBase + size * 2
                        for (i in 0 until size) {
                            val kIdx = keysBase + i * 2
                            val tIdx = targetsBase + i * 2
                            if (kIdx + 1 < insns.size && tIdx + 1 < insns.size) {
                                val kVal = ((insns[kIdx + 1].toInt() and 0xFFFF) shl 16) or (insns[kIdx].toInt() and 0xFFFF)
                                val tVal = ((insns[tIdx + 1].toInt() and 0xFFFF) shl 16) or (insns[tIdx].toInt() and 0xFFFF)
                                kList.add(kVal)
                                tList.add(startPc + tVal)
                            }
                        }
                        keys = kList
                        targets = tList
                    }

                    DexInstruction(
                        offset = startPc,
                        opcode = opcode,
                        opcodeName = "sparse-switch",
                        registers = listOf(vA),
                        targetOffset = payloadOffset,
                        switchKeys = keys,
                        switchTargets = targets
                    )
                }

                in 0x2d..0x31 -> { // cmpl-float, cmpg-float, cmpl-double, cmpg-double, cmp-long
                    val vA = (word0 shr 8) and 0xFF
                    val word1 = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val vB = word1 and 0xFF
                    val vC = (word1 shr 8) and 0xFF
                    pc += 2
                    val name = when (opcode) {
                        0x2d -> "cmpl-float"; 0x2e -> "cmpg-float"; 0x2f -> "cmpl-double"
                        0x30 -> "cmpg-double"; else -> "cmp-long"
                    }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB, vC))
                }

                in 0x32..0x37 -> { // if-test (if-eq, if-ne, if-lt, if-ge, if-gt, if-le)
                    val vA = (word0 shr 8) and 0x0F
                    val vB = (word0 shr 12) and 0x0F
                    val relOffset = if (pc + 1 < insns.size) insns[pc + 1].toInt() else 0
                    pc += 2
                    val name = when (opcode) {
                        0x32 -> "if-eq"; 0x33 -> "if-ne"; 0x34 -> "if-lt"
                        0x35 -> "if-ge"; 0x36 -> "if-gt"; else -> "if-le"
                    }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB), targetOffset = startPc + relOffset)
                }

                in 0x38..0x3d -> { // if-testz (if-eqz, if-nez, if-ltz, if-gez, if-gtz, if-lez)
                    val vA = (word0 shr 8) and 0xFF
                    val relOffset = if (pc + 1 < insns.size) insns[pc + 1].toInt() else 0
                    pc += 2
                    val name = when (opcode) {
                        0x38 -> "if-eqz"; 0x39 -> "if-nez"; 0x3a -> "if-ltz"
                        0x3b -> "if-gez"; 0x3c -> "if-gtz"; else -> "if-lez"
                    }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA), targetOffset = startPc + relOffset)
                }

                in 0x44..0x51 -> { // aget* / aput*
                    val vA = (word0 shr 8) and 0xFF
                    val word1 = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val vB = word1 and 0xFF
                    val vC = (word1 shr 8) and 0xFF
                    pc += 2
                    val name = when (opcode) {
                        0x44 -> "aget"; 0x45 -> "aget-wide"; 0x46 -> "aget-object"; 0x47 -> "aget-boolean"
                        0x48 -> "aget-byte"; 0x49 -> "aget-char"; 0x4a -> "aget-short"; 0x4b -> "aput"
                        0x4c -> "aput-wide"; 0x4d -> "aput-object"; 0x4e -> "aput-boolean"
                        0x4f -> "aput-byte"; 0x50 -> "aput-char"; else -> "aput-short"
                    }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB, vC))
                }

                in 0x52..0x5f -> { // iget / iput
                    val vA = (word0 shr 8) and 0x0F
                    val vB = (word0 shr 12) and 0x0F
                    val fieldIdx = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    pc += 2
                    val field = if (fieldIdx in fields.indices) fields[fieldIdx] else null
                    val fieldStr = field?.let { "${it.classType}->${it.name}:${it.type}" } ?: "field@$fieldIdx"
                    val name = when (opcode) {
                        0x52 -> "iget"; 0x53 -> "iget-wide"; 0x54 -> "iget-object"; 0x55 -> "iget-boolean"
                        0x56 -> "iget-byte"; 0x57 -> "iget-char"; 0x58 -> "iget-short"; 0x59 -> "iput"
                        0x5a -> "iput-wide"; 0x5b -> "iput-object"; 0x5c -> "iput-boolean"
                        0x5d -> "iput-byte"; 0x5e -> "iput-char"; else -> "iput-short"
                    }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB), targetField = fieldStr)
                }

                in 0x60..0x6d -> { // sget / sput
                    val vA = (word0 shr 8) and 0xFF
                    val fieldIdx = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    pc += 2
                    val field = if (fieldIdx in fields.indices) fields[fieldIdx] else null
                    val fieldStr = field?.let { "${it.classType}->${it.name}:${it.type}" } ?: "field@$fieldIdx"
                    val name = when (opcode) {
                        0x60 -> "sget"; 0x61 -> "sget-wide"; 0x62 -> "sget-object"; 0x63 -> "sget-boolean"
                        0x64 -> "sget-byte"; 0x65 -> "sget-char"; 0x66 -> "sget-short"; 0x67 -> "sput"
                        0x68 -> "sput-wide"; 0x69 -> "sput-object"; 0x6a -> "sput-boolean"
                        0x6b -> "sput-byte"; 0x6c -> "sput-char"; else -> "sput-short"
                    }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA), targetField = fieldStr)
                }

                in 0x6e..0x72 -> { // invoke-kind (virtual, super, direct, static, interface)
                    val count = (word0 shr 12) and 0x0F
                    val methodIdx = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val word2 = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    val vC = (word0 shr 8) and 0x0F
                    val vD = word2 and 0x0F
                    val vE = (word2 shr 4) and 0x0F
                    val vF = (word2 shr 8) and 0x0F
                    val vG = (word2 shr 12) and 0x0F
                    val allRegs = listOf(vC, vD, vE, vF, vG).take(count)
                    pc += 3

                    val method = if (methodIdx in methods.indices) methods[methodIdx] else null
                    val methodStr = method?.let {
                        "${it.classType}->${it.name}(${it.proto.parameters.joinToString("")})${it.proto.returnType}"
                    } ?: "method@$methodIdx"

                    val name = when (opcode) {
                        0x6e -> "invoke-virtual"; 0x6f -> "invoke-super"; 0x70 -> "invoke-direct"
                        0x71 -> "invoke-static"; else -> "invoke-interface"
                    }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = allRegs, targetMethod = methodStr)
                }

                in 0x74..0x78 -> { // invoke-kind/range
                    val count = (word0 shr 8) and 0xFF
                    val methodIdx = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val vC = if (pc + 2 < insns.size) insns[pc + 2].toInt() and 0xFFFF else 0
                    val regs = (0 until count).map { vC + it }
                    pc += 3

                    val method = if (methodIdx in methods.indices) methods[methodIdx] else null
                    val methodStr = method?.let {
                        "${it.classType}->${it.name}(${it.proto.parameters.joinToString("")})${it.proto.returnType}"
                    } ?: "method@$methodIdx"

                    val name = when (opcode) {
                        0x74 -> "invoke-virtual/range"; 0x75 -> "invoke-super/range"; 0x76 -> "invoke-direct/range"
                        0x77 -> "invoke-static/range"; else -> "invoke-interface/range"
                    }
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = regs, targetMethod = methodStr)
                }

                in 0x7b..0x8f -> { // unops and numeric type conversions
                    val vA = (word0 shr 8) and 0x0F
                    val vB = (word0 shr 12) and 0x0F
                    pc += 1
                    val name = getUnopName(opcode)
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB))
                }

                in 0x90..0xaf -> { // binop (int, long, float, double)
                    val vA = (word0 shr 8) and 0xFF
                    val word1 = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val vB = word1 and 0xFF
                    val vC = (word1 shr 8) and 0xFF
                    pc += 2
                    val name = getBinopName(opcode)
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB, vC))
                }

                in 0xb0..0xcf -> { // binop/2addr
                    val vA = (word0 shr 8) and 0x0F
                    val vB = (word0 shr 12) and 0x0F
                    pc += 1
                    val name = getBinop2AddrName(opcode)
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB))
                }

                in 0xd0..0xd7 -> { // binop/lit16
                    val vA = (word0 shr 8) and 0x0F
                    val vB = (word0 shr 12) and 0x0F
                    val lit = if (pc + 1 < insns.size) insns[pc + 1].toLong() else 0L
                    pc += 2
                    val name = getBinopLit16Name(opcode)
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB), literal = lit)
                }

                in 0xd8..0xe2 -> { // binop/lit8
                    val vA = (word0 shr 8) and 0xFF
                    val word1 = if (pc + 1 < insns.size) insns[pc + 1].toInt() and 0xFFFF else 0
                    val vB = word1 and 0xFF
                    val lit = (word1 shr 8).toByte().toLong()
                    pc += 2
                    val name = getBinopLit8Name(opcode)
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = name, registers = listOf(vA, vB), literal = lit)
                }

                0xfa -> { // invoke-polymorphic
                    pc += 4
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "invoke-polymorphic")
                }

                0xfc -> { // invoke-custom
                    pc += 3
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "invoke-custom")
                }

                else -> {
                    // Unknown opcode fallback
                    pc += 1
                    val hex = Integer.toHexString(opcode).padStart(2, '0')
                    DexInstruction(offset = startPc, opcode = opcode, opcodeName = "op_$hex")
                }
            }

            result.add(instr)
        }

        return result
    }

    private fun getUnopName(op: Int): String = when (op) {
        0x7b -> "neg-int"; 0x7c -> "not-int"; 0x7d -> "neg-long"; 0x7e -> "not-long"
        0x7f -> "neg-float"; 0x80 -> "neg-double"; 0x81 -> "int-to-long"; 0x82 -> "int-to-float"
        0x83 -> "int-to-double"; 0x84 -> "long-to-int"; 0x85 -> "long-to-float"; 0x86 -> "long-to-double"
        0x87 -> "float-to-int"; 0x88 -> "float-to-long"; 0x89 -> "float-to-double"; 0x8a -> "double-to-int"
        0x8b -> "double-to-long"; 0x8c -> "double-to-float"; 0x8d -> "int-to-byte"; 0x8e -> "int-to-char"
        0x8f -> "int-to-short"; else -> "unop_${Integer.toHexString(op)}"
    }

    private fun getBinopName(op: Int): String = when (op) {
        0x90 -> "add-int"; 0x91 -> "sub-int"; 0x92 -> "mul-int"; 0x93 -> "div-int"
        0x94 -> "rem-int"; 0x95 -> "and-int"; 0x96 -> "or-int"; 0x97 -> "xor-int"
        0x98 -> "shl-int"; 0x99 -> "shr-int"; 0x9a -> "ushr-int"
        0x9b -> "add-long"; 0x9c -> "sub-long"; 0x9d -> "mul-long"; 0x9e -> "div-long"
        0x9f -> "rem-long"; 0xa0 -> "and-long"; 0xa1 -> "or-long"; 0xa2 -> "xor-long"
        0xa3 -> "shl-long"; 0xa4 -> "shr-long"; 0xa5 -> "ushr-long"
        0xa6 -> "add-float"; 0xa7 -> "sub-float"; 0xa8 -> "mul-float"; 0xa9 -> "div-float"; 0xaa -> "rem-float"
        0xab -> "add-double"; 0xac -> "sub-double"; 0xad -> "mul-double"; 0xae -> "div-double"; 0xaf -> "rem-double"
        else -> "binop_${Integer.toHexString(op)}"
    }

    private fun getBinop2AddrName(op: Int): String = when (op) {
        0xb0 -> "add-int/2addr"; 0xb1 -> "sub-int/2addr"; 0xb2 -> "mul-int/2addr"
        0xb3 -> "div-int/2addr"; 0xb4 -> "rem-int/2addr"; 0xb5 -> "and-int/2addr"
        0xb6 -> "or-int/2addr"; 0xb7 -> "xor-int/2addr"; 0xb8 -> "shl-int/2addr"
        0xb9 -> "shr-int/2addr"; 0xba -> "ushr-int/2addr"
        0xbb -> "add-long/2addr"; 0xbc -> "sub-long/2addr"; 0xbd -> "mul-long/2addr"
        0xbe -> "div-long/2addr"; 0xbf -> "rem-long/2addr"; 0xc0 -> "and-long/2addr"
        0xc1 -> "or-long/2addr"; 0xc2 -> "xor-long/2addr"; 0xc3 -> "shl-long/2addr"
        0xc4 -> "shr-long/2addr"; 0xc5 -> "ushr-long/2addr"
        0xc6 -> "add-float/2addr"; 0xc7 -> "sub-float/2addr"; 0xc8 -> "mul-float/2addr"
        0xc9 -> "div-float/2addr"; 0xca -> "rem-float/2addr"
        0xcb -> "add-double/2addr"; 0xcc -> "sub-double/2addr"; 0xcd -> "mul-double/2addr"
        0xce -> "div-double/2addr"; 0xcf -> "rem-double/2addr"
        else -> "binop2addr_${Integer.toHexString(op)}"
    }

    private fun getBinopLit16Name(op: Int): String = when (op) {
        0xd0 -> "add-int/lit16"; 0xd1 -> "rsub-int"; 0xd2 -> "mul-int/lit16"
        0xd3 -> "div-int/lit16"; 0xd4 -> "rem-int/lit16"; 0xd5 -> "and-int/lit16"
        0xd6 -> "or-int/lit16"; 0xd7 -> "xor-int/lit16"; else -> "binoplit16_${Integer.toHexString(op)}"
    }

    private fun getBinopLit8Name(op: Int): String = when (op) {
        0xd8 -> "add-int/lit8"; 0xd9 -> "rsub-int/lit8"; 0xda -> "mul-int/lit8"
        0xdb -> "div-int/lit8"; 0xdc -> "rem-int/lit8"; 0xdd -> "and-int/lit8"
        0xde -> "or-int/lit8"; 0xdf -> "xor-int/lit8"; 0xe0 -> "shl-int/lit8"
        0xe1 -> "shr-int/lit8"; 0xe2 -> "ushr-int/lit8"; else -> "binoplit8_${Integer.toHexString(op)}"
    }
}
