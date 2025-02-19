package com.geeksville.mesh.model

import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.ConfigProtos
import com.google.protobuf.ByteString

/** Utility function to make it easy to declare byte arrays - FIXME move someplace better */
fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }
fun xorHash(b: ByteArray) = b.fold(0) { acc, x -> acc xor (x.toInt() and 0xff) }

data class Channel(
    val settings: ChannelProtos.ChannelSettings,
    val loraConfig: ConfigProtos.Config.LoRaConfig
) {
    companion object {
        // These bytes must match the well known and not secret bytes used the default channel AES128 key device code
        private val channelDefaultKey = byteArrayOfInts(
            0xd4, 0xf1, 0xbb, 0x3a, 0x20, 0x29, 0x07, 0x59,
            0xf0, 0xbc, 0xff, 0xab, 0xcf, 0x4e, 0x69, 0x01
        )

        private val cleartextPSK = ByteString.EMPTY
        private val defaultPSK =
            byteArrayOfInts(1) // a shortstring code to indicate we need our default PSK

        // The default channel that devices ship with
        val default = Channel(
            ChannelProtos.ChannelSettings.newBuilder()
                .setPsk(ByteString.copyFrom(defaultPSK))
                .build(),
            ConfigProtos.Config.LoRaConfig.newBuilder()
                .setModemPreset(ConfigProtos.Config.LoRaConfig.ModemPreset.LongFast)
                .build()
        )
    }

    /// Return the name of our channel as a human readable string.  If empty string, assume "Default" per mesh.proto spec
    val name: String
        get() = settings.name.ifEmpty {
            // We have a new style 'empty' channel name.  Use the same logic from the device to convert that to a human readable name
            if (loraConfig.bandwidth != 0)
                "Unset"
            else when (loraConfig.modemPreset) {
                ConfigProtos.Config.LoRaConfig.ModemPreset.ShortFast -> "ShortFast"
                ConfigProtos.Config.LoRaConfig.ModemPreset.ShortSlow -> "ShortSlow"
                ConfigProtos.Config.LoRaConfig.ModemPreset.MedFast -> "MidFast"
                ConfigProtos.Config.LoRaConfig.ModemPreset.MedSlow -> "MidSlow"
                ConfigProtos.Config.LoRaConfig.ModemPreset.LongFast -> "LongFast"
                ConfigProtos.Config.LoRaConfig.ModemPreset.LongSlow -> "LongSlow"
                ConfigProtos.Config.LoRaConfig.ModemPreset.VLongSlow -> "VLongSlow"
                else -> "Invalid"
            }
        }

    val psk: ByteString
        get() = if (settings.psk.size() != 1)
            settings.psk // A standard PSK
        else {
            // One of our special 1 byte PSKs, see mesh.proto for docs.
            val pskIndex = settings.psk.byteAt(0).toInt()

            if (pskIndex == 0)
                cleartextPSK
            else {
                // Treat an index of 1 as the old channelDefaultKey and work up from there
                val bytes = channelDefaultKey.clone()
                bytes[bytes.size - 1] = (0xff and (bytes[bytes.size - 1] + pskIndex - 1)).toByte()
                ByteString.copyFrom(bytes)
            }
        }

    /**
     * Return a name that is formatted as #channename-suffix
     *
     * Where suffix indicates the hash of the PSK
     */
    val humanName: String
        get() {
            // start with the PSK then xor in the name
            val pskCode = xorHash(psk.toByteArray())
            val nameCode = xorHash(name.toByteArray())
            val suffix = 'A' + ((pskCode xor nameCode) % 26)

            return "#${name}-${suffix}"
        }

    override fun equals(other: Any?): Boolean = (other is Channel)
            && psk.toByteArray() contentEquals other.psk.toByteArray()
            && name == other.name

    override fun hashCode(): Int {
        var result = settings.hashCode()
        result = 31 * result + loraConfig.hashCode()
        return result
    }
}
