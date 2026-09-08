package ch.hippmann.godot.replication.core.codec

public object HalfFloat {
    public fun toHalfBits(value: Float): Int {
        val bits = value.toRawBits()
        val sign = (bits ushr 16) and 0x8000
        var exponent = ((bits ushr 23) and 0xFF) - 127 + 15
        val mantissa = bits and 0x7F_FFFF

        if (exponent >= 0x1F) {
            val isNaN = ((bits ushr 23) and 0xFF) == 0xFF && mantissa != 0
            return sign or 0x7C00 or (if (isNaN) 0x200 else 0)
        }
        if (exponent <= 0) {
            if (exponent < -10) return sign
            val subnormalMantissa = (mantissa or 0x80_0000) shr (1 - exponent)
            return sign or roundMantissa(subnormalMantissa)
        }
        exponent = exponent shl 10
        return sign or roundHalf(exponent or (mantissa shr 13), mantissa and 0x1FFF)
    }

    public fun fromHalfBits(bits: Int): Float {
        val sign = (bits and 0x8000) shl 16
        val exponent = (bits ushr 10) and 0x1F
        val mantissa = bits and 0x3FF

        val floatBits = when (exponent) {
            0 -> if (mantissa == 0) sign else subnormalToFloatBits(sign, mantissa)
            0x1F -> sign or 0x7F80_0000 or (mantissa shl 13)
            else -> sign or ((exponent + 127 - 15) shl 23) or (mantissa shl 13)
        }
        return Float.fromBits(floatBits)
    }

    private fun subnormalToFloatBits(sign: Int, mantissa: Int): Int {
        var exponent = 127 - 15 + 1
        var shiftedMantissa = mantissa
        while (shiftedMantissa and 0x400 == 0) {
            shiftedMantissa = shiftedMantissa shl 1
            exponent--
        }
        return sign or (exponent shl 23) or ((shiftedMantissa and 0x3FF) shl 13)
    }

    private fun roundMantissa(mantissa: Int): Int {
        val rounded = mantissa shr 13
        val remainder = mantissa and 0x1FFF
        return if (remainder > 0x1000 || (remainder == 0x1000 && rounded and 1 == 1)) rounded + 1 else rounded
    }

    private fun roundHalf(truncated: Int, remainder: Int): Int =
        if (remainder > 0x1000 || (remainder == 0x1000 && truncated and 1 == 1)) truncated + 1 else truncated
}
