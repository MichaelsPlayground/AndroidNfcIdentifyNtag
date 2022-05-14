This app checks a NFC tag that it is from manufacturer NXP.

The second check is on the type of card, it can detect a NTAG od type NTAG213, NTAG215 or NTAG216.

The source is based on a stackoverflow.com answer:

https://stackoverflow.com/questions/37002498/distinguish-ntag213-from-mf0icu2

Distinguish NTAG213 from MF0ICU2
Ask Question
Asked 6 years ago
Modified 6 years ago
Viewed 1k times

Is there any way to distinguish a NTAG213 from a MF0ICU2 tag based on its UID, ATQA or SAK values? As I have to program the tags differently (PWD/PACK for NTAG213 or 3DES for MF0ICU2) there must be a way to call either one or another method.

Unfortunately, the Android framework tells me that both tags are MifareUltralight with type TYPE_ULTRALIGHT_C. The ATQA (0x0044) and SAK (0x00) are identical, too.

Other apps like NFC TagInfo by NXP can tell me the exact type of a tag, so I know that there must be some way.

1 Answer

Once you know that the tag is an NXP tag (UID starts with 0x04), you would

first send a GET_VERSION command. If this command succeeds, you know that the tag is EV1 or later (MIFARE Ultralight EV1, NTAG21x). Otherwise, you can assume that it is a first generation tag (MIFARE Ultralight, Ultralight C, NTAG203).
If the tag is an EV1 tag, you can continue by analyzing the resonse to the GET_VERSION command. This will reveal the product type (NTAG or Ultralight EV1) as well as product subtype, product version and storage size (which allows you to determine the exact chip type:

+------------+------+---------+-----------+--------------+
| Chip       | Type | Subtype | Version   | Storage size |
+------------+------+---------+-----------+--------------+
| NTAG210    | 0x04 | 0x01    | 0x01 0x00 | 0x0B         |
| NTAG212    | 0x04 | 0x01    | 0x01 0x00 | 0x0E         |
| NTAG213    | 0x04 | 0x02    | 0x01 0x00 | 0x0F         |
| NTAG213F   | 0x04 | 0x04    | 0x01 0x00 | 0x0F         |
| NTAG215    | 0x04 | 0x02    | 0x01 0x00 | 0x11         |
| NTAG216    | 0x04 | 0x02    | 0x01 0x00 | 0x13         |
| NTAG216F   | 0x04 | 0x04    | 0x01 0x00 | 0x13         |
+------------+------+---------+-----------+--------------+
| NT3H1101   | 0x04 | 0x02    | 0x01 0x01 | 0x13         |
| NT3H1101W0 | 0x04 | 0x05    | 0x02 0x01 | 0x13         |
| NT3H2111W0 | 0x04 | 0x05    | 0x02 0x02 | 0x13         |
| NT3H2101   | 0x04 | 0x02    | 0x01 0x01 | 0x15         |
| NT3H1201W0 | 0x04 | 0x05    | 0x02 0x01 | 0x15         |
| NT3H2211W0 | 0x04 | 0x05    | 0x02 0x02 | 0x15         |
+------------+------+---------+-----------+--------------+
| MF0UL1101  | 0x03 | 0x01    | 0x01 0x00 | 0x0B         |
| MF0ULH1101 | 0x03 | 0x02    | 0x01 0x00 | 0x0B         |
| MF0UL2101  | 0x03 | 0x01    | 0x01 0x00 | 0x0E         |
| MF0ULH2101 | 0x03 | 0x02    | 0x01 0x00 | 0x0E         |
+------------+------+---------+-----------+--------------+
If the tag is not an EV1 tag, you can send an AUTHENTICATE (part 1) command. If this command succeeds, you know that the tag is MIFARE Ultralight C. Otherwise, you can assume that the tag is either Ultralight or NTAG203.
In order to distinguish between MIFARE Ultralight and NTAG203, you can try to read pages that do not exist on Ultralight (e.g. read page 41).
You can send commands to the tag using the NfcA or MifareUltralight (if even available for the tag) tag technologies:

boolean testCommand(NfcA nfcA, byte[] command) throws IOException {
final boolean leaveConnected = nfcA.isConnected();

    boolean commandAvailable = false;

    if (!leaveConnected) {
        nfcA.connect();
    }

    try {
        byte[] result = nfcA.transceive(command);
        if ((result != null) &&
            (result.length > 0) &&
            !((result.length == 1) && ((result[0] & 0x00A) == 0x000))) {
            // some response received and response is not a NACK response
            commandAvailable = true;

            // You might also want to check if you received a response
            // that is plausible for the specific command before you
            // assume that the command is actualy available and what
            // you expected...
        }
    } catch (IOException e) {
        // IOException (including TagLostException) could indicate that
        // either the tag is no longer in range or that the command is
        // not supported by the tag 
    }

    try {
        nfcA.close();
    } catch (Exception e) {}

    if (leaveConnected) {
        nfcA.connect();
    }

    return commandAvailable;
}
Note that some NFC stacks will generate an IOException (typically a TagLostException) when a command is not supported by the tag. Regardless of receiving a NACK response or an IOException for an unsupported command, you should disconnect and reconnect the tag afterwards in order to reset the state of the tag before you continue to send other commands.

edited May 11, 2016 at 16:31
answered May 5, 2016 at 9:46
user Michael Roland