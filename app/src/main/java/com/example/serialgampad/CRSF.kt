package com.example.serialgampad
import kotlin.experimental.and
import kotlin.experimental.xor
import kotlin.system.exitProcess

const val CRSF_SYNC: Byte = 0xC8.toByte()
fun userOut(string: String) {
    println(string)
}
enum class Addresses(val value: Int){
    CRSF_ADDRESS_BROADCAST(0),
    CRSF_ADDRESS_FLIGHT_CONTROLLER(200),
    CRSF_ADDRESS_HANDSET(234),
    CRSF_ADDRESS_CRSF_RECEIVER(236),
    CRSF_ADDRESS_CRSF_TRANSMITTER(238),
    CRSF_ADDRESS_ELRS_LUA(239)
}
enum class PacketTypes(val value: Int) {
    GPS(0x02),
    VARIO(0x07),
    BATTERY_SENSOR(0x08),
    BARO_ALT(0x09),
    HEARTBEAT(0x0B),
    VIDEO_TRANSMITTER(0x0F),
    LINK_STATISTICS(0x14),
    RC_CHANNELS_PACKED(0x16),
    ATTITUDE(0x1E),
    FLIGHT_MODE(0x21),
    DEVICE_INFO(0x29),
    PARAMETER_INFO_ENTRY(0x2B),
    CONFIG_READ(0x2C),
    CONFIG_WRITE(0x2D),
    RADIO_ID(0x3A)
}

fun handleCRSFPacket(ptype: Int, data: ByteArray) {
    when (ptype) {
        PacketTypes.FLIGHT_MODE.value -> {
            val mode = data.sliceArray(3..data.lastIndex - 2).decodeToString()
            userOut("Flight mode: $mode")
        }
//   uint8_t uplink_RSSI_1;
//    uint8_t uplink_RSSI_2;
//    uint8_t uplink_Link_quality;
//    int8_t uplink_SNR;
//    uint8_t active_antenna;
//    uint8_t rf_Mode;
//    uint8_t uplink_TX_Power;
//    uint8_t downlink_RSSI_1;
//    uint8_t downlink_Link_quality;
//    int8_t downlink_SNR;
        PacketTypes.LINK_STATISTICS.value -> {
            val Rrssi1 = signedByte(data[3])
            val Rrssi2 = signedByte(data[4])
            val Rlq = data[5].toInt()
            val Rsnr = signedByte(data[6])
            val activeAntenna = signedByte(data[7])
            val packetRate = data[8].toInt()
            val uplinkTXPower = signedByte(data[9])
            val Trss1 =signedByte(data[10])
            val Tlq = data[11].toInt()
            val Tsnr = signedByte(data[12])
            val stats= arrayOf( signedByte(data[3]), signedByte(data[4]), data[5].toInt(), signedByte(data[6]), signedByte(data[7]), data[8].toInt(), signedByte(data[9]),signedByte(data[10]), data[11].toInt(), signedByte(data[12]))
//            userOut("RSSI1=$Rrssi1 RSSI2=$Rrssi2 LQ=$Rlq mode=$packetRate snr=$Rsnr")
            stats.forEach { print("$it,") }
        }

        PacketTypes.ATTITUDE.value -> {
            val pitch = data.sliceArray(3..4).toIntBigEndian() / 10000.0
            val roll = data.sliceArray(5..6).toIntBigEndian() / 10000.0
            val yaw = data.sliceArray(7..8).toIntBigEndian() / 10000.0
            userOut("Attitude: Pitch=$pitch Roll=$roll Yaw=$yaw")
        }

        PacketTypes.RADIO_ID.value -> {
            userOut("des=${data[3]} src=${data[4]}")
            if (data.size >= 6 && data[5].toInt() == 0x10) {
                userOut("RADIO_ID: Synchronization packet received")
            } else {
                val packetData = data.joinToString(" ") { it.toString(16) }
                userOut("RADIO_ID: Unknown content: $packetData")
            }
        }

        PacketTypes.GPS.value -> {
            if (data.size >= 18) {
                val latitude = data.sliceArray(3..6).toIntBigEndian().toDouble() / 1e7
                val longitude = data.sliceArray(7..10).toIntBigEndian().toDouble() / 1e7
                val groundSpeed = data.sliceArray(11..12).toIntBigEndian().toDouble() / 36.0 // Convert knots to m/s
                val heading = data.sliceArray(13..14).toIntBigEndian().toDouble() / 100.0
                val altitude = data.sliceArray(15..16).toIntBigEndian() - 1000
                val satellites = data[17].toInt() and 0xFF // Unsigned byte

                userOut(
                    "GPS: Lat=$latitude Lon=$longitude Speed=${"%.1f".format(groundSpeed)}m/s Hdg=${
                        "%.1f".format(
                            heading
                        )
                    }° Alt=$altitude m Sats=$satellites"
                )
            } else {
                userOut("Invalid GPS packet size: ${data.size}")
            }
        }
        PacketTypes.PARAMETER_INFO_ENTRY.value -> {
            userOut("parameter update ${data.sliceArray(2..<data.lastIndex).decodeToString()}")
        }

        else -> userOut("Unknown packet type: $ptype")
    }
}

fun crc8DvbS2(crc: Byte, a: Byte): Byte {
    var crcTemp: Byte = crc xor a
    repeat(8) {
        crcTemp = if (crcTemp and 0x80.toByte() != 0.toByte()) {
            (crcTemp.toInt().shl(1)).toByte() xor 0xD5.toByte()
        } else {
            crcTemp.toInt().shl(1).toByte()
        }
    }
    return crcTemp and 0xFF.toByte()
}

fun crc8Data(data: ByteArray): Int {
    var crc = 0x00.toByte()
    for (byte in data) {
        crc = crc8DvbS2(crc, byte)
    }
    return crc.toInt()
}

fun CRSFValidateFrame(frame: ByteArray): Boolean {
    return crc8Data(frame.sliceArray(2..<frame.size - 1)) == frame.last().toInt()
}

fun signedByte(b: Byte): Int {
    return if (b.toInt() >= 128) b.toInt() - 256 else b.toInt()
}

fun packCRSFToBytes(channels: List<Int>): ByteArray {
    if (channels.size != 16) {
        throw IllegalArgumentException("CRSF must have 16 channels")
    }

    val result = mutableListOf<Byte>()
    var destShift = 0
    var newVal = 0

    for (ch in channels) {
        newVal = newVal or ((ch shl destShift) and 0xFF)
        result.add(newVal.toByte())
        val srcBitsLeft = 11 - 8 + destShift
        newVal = ch shr (11 - srcBitsLeft)

        if (srcBitsLeft >= 8) {
            result.add((newVal and 0xFF).toByte())
            newVal = newVal shr 8
        }

        destShift = srcBitsLeft % 8
    }

    return result.toByteArray()
}

fun channelsCRSFToChannelsPacket(channels: List<Int>): ByteArray {
    val result = mutableListOf<Byte>()
    result.add(CRSF_SYNC)
    result.add(24.toByte())
    result.add(PacketTypes.RC_CHANNELS_PACKED.value.toByte())
    result.addAll(packCRSFToBytes(channels).toList())
    result.add(crc8Data(result.drop(2).toByteArray()).toByte())
    return result.toByteArray()
}




fun ByteArray.toIntBigEndian(): Int {
    return this.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
}
