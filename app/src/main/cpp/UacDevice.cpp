#include "UacDevice.h"
#include "Utils.h"

#include <algorithm>

static const uint8_t AUDIO_CONTROL_INTERFACE = 0; // TODO: Should be taken from the descriptor
static const uint8_t AUDIO_OUTPUT_INTERFACE = 1; // TODO: Should be taken from the descriptor
static const uint8_t AUDIO_INPUT_INTERFACE = 2; // TODO: Should be taken from the descriptor

static const uint8_t AUDIO_OUTPUT_STREAMING_EP = 0x01; // TODO: Should be taken from the descriptor
static const uint8_t AUDIO_INPUT_STREAMING_EP = 0x82; // TODO: Should be taken from the descriptor

static const uint8_t AUDIO_OUTPUT_CTRL_UNIT = 0x02U; // TODO: Should be taken from the descriptor
static const uint8_t AUDIO_INPUT_CTRL_UNIT = 0x06U; // TODO: Should be taken from the descriptor

static const uint8_t SAMPLE_SIZE_16BIT_ALTSETTING = 1; // TODO: Should be taken from the descriptor

static const uint8_t AUDIO_REQ_GET_CUR = 0x81U;
static const uint8_t AUDIO_REQ_GET_MIN = 0x82U;
static const uint8_t AUDIO_REQ_GET_MAX = 0x83U;
static const uint8_t AUDIO_REQ_SET_CUR = 0x01U;
static const uint8_t AUDIO_REQ_SET_MIN = 0x02U;
static const uint8_t AUDIO_REQ_SET_MAX = 0x03U;


static const uint8_t AUDIO_CONTROL_SELECTOR_MUTE = 0x01U;
static const uint8_t AUDIO_CONTROL_SELECTOR_VOLUME = 0x02U;

static const uint8_t SAMPLING_FREQ_CONTROL = 0x01U;

static const uint16_t OUTPUT_PACKET_SIZE = (44100 / 1000) * 2 * 2; // 1 ms of audio at 44.1kHz rate, 2 bytes per sample, 2 channels
static const uint16_t INPUT_PACKET_SIZE = (44100 / 1000) * 3 * 2 ; // 1 ms of audio at 44.1kHz rate, 3 bytes per sample, 2 channels

UacDevice::UacDevice(jint fd)
    : device(fd)
{
    device.openInterface(AUDIO_CONTROL_INTERFACE);
    device.openInterface(AUDIO_OUTPUT_INTERFACE);
    device.openInterface(AUDIO_INPUT_INTERFACE);
}

UacDevice::~UacDevice()
{
    device.closeInterface(AUDIO_CONTROL_INTERFACE);
    device.closeInterface(AUDIO_OUTPUT_INTERFACE);
    device.closeInterface(AUDIO_INPUT_INTERFACE);
}

void UacDevice::prepareAudioOutput()
{
    // Select interface configuration with 16bit sample size
    device.setAltsetting(AUDIO_OUTPUT_INTERFACE, SAMPLE_SIZE_16BIT_ALTSETTING);
}

void UacDevice::prepareAudioInput()
{
    // Select interface configuration with 24bit sample size
    device.setAltsetting(AUDIO_INPUT_INTERFACE, SAMPLE_SIZE_16BIT_ALTSETTING);
}

void UacDevice::setChannelVolume(Channel channel, int volume)
{
    setControlValue(AUDIO_REQ_SET_CUR, channel, AUDIO_CONTROL_SELECTOR_VOLUME, volume, sizeof(int16_t));
}

int UacDevice::getChannelVolume(Channel channel)
{
    return (int16_t)getControlValue(AUDIO_REQ_GET_CUR, channel, AUDIO_CONTROL_SELECTOR_VOLUME, sizeof(int16_t));
}

int UacDevice::getChannelMinVolume(Channel channel)
{
    return (int16_t)getControlValue(AUDIO_REQ_GET_MIN, channel, AUDIO_CONTROL_SELECTOR_VOLUME, sizeof(int16_t));
}

int UacDevice::getChannelMaxVolume(Channel channel)
{
    return (int16_t)getControlValue(AUDIO_REQ_GET_MAX, channel, AUDIO_CONTROL_SELECTOR_VOLUME, sizeof(int16_t));
}

int UacDevice::getChannelMute(Channel channel)
{
    return getControlValue(AUDIO_REQ_GET_CUR, channel, AUDIO_CONTROL_SELECTOR_MUTE, sizeof(uint8_t));
}

int UacDevice::getControlValue(uint8_t reqType, Channel channel, uint8_t control, uint16_t valueSize)
{
    int32_t res = 0;

    device.getControlAttr(false, // Interface
        reqType,
        control << 8, // target control
        getCtrlUnitForChannel(channel) << 8 | AUDIO_CONTROL_INTERFACE,
        std::min(valueSize, (uint16_t)sizeof(res)),
        (unsigned char*)&res
        );

    return res;
}

void UacDevice::setControlValue(uint8_t reqType, Channel channel, uint8_t control, int value, uint16_t valueSize)
{
    device.setControlAttr(false, // Interface
        reqType,
        control << 8, // target control
        getCtrlUnitForChannel(channel) << 8 | AUDIO_CONTROL_INTERFACE,
        std::min(valueSize, (uint16_t)sizeof(int)),
        (unsigned char*)&value
        );
}

void UacDevice::setChannelSampleRate(Channel channel, int rate)
{
    device.setControlAttr(true, // Endpoint
        AUDIO_REQ_SET_CUR, // Request type
        SAMPLING_FREQ_CONTROL << 8, // selector type
        getEpForChannel(channel), // endpoint number
        3, // yes 3, not 4
        (unsigned char*)&rate
        );
}

int UacDevice::getChannelSampleRate(Channel channel)
{
    uint32_t res = 0;

    device.getControlAttr(true, // Endpoint
        AUDIO_REQ_GET_CUR, // Request type
        SAMPLING_FREQ_CONTROL << 8, // selector type
        getEpForChannel(channel), // endpoint number
        3, // yes 3, not 4
        (unsigned char*)&res
        );

    return res;
}

uint8_t UacDevice::getEpForChannel(Channel channel)
{
    return channel == Output ? AUDIO_OUTPUT_STREAMING_EP : AUDIO_INPUT_STREAMING_EP;
}

uint8_t UacDevice::getInterfaceForChannel(Channel channel)
{
    return channel == Output ? AUDIO_OUTPUT_INTERFACE : AUDIO_INPUT_INTERFACE;
}

uint8_t UacDevice::getCtrlUnitForChannel(Channel channel)
{
    return channel == Output ? AUDIO_OUTPUT_CTRL_UNIT : AUDIO_INPUT_CTRL_UNIT;
}

void UacDevice::playPCM(unsigned char * data, size_t size)
{
//    device.sendIsoData(AUDIO_OUTPUT_STREAMING_EP,
//                           data,
//                           size,
//                           OUTPUT_PACKET_SIZE);
    device.startTransferLoop();

    int sampleRate = getChannelSampleRate(UacDevice::Output);

    if(sampleRate % 1000 == 0)
    {
        size_t packetSize = sampleRate / 1000 * 2 * 2; // 2 channels 16 bit
        size_t bytesToGo = size;

        while(bytesToGo > 0)
        {
            device.sendIsoPacket(AUDIO_OUTPUT_STREAMING_EP, data, packetSize);
            data += packetSize;
            bytesToGo -= packetSize;
        }
    }
    else
    {
        printf("UacDevice::playPCM(): odd sample rates are not yet implemented\n");
    }

    device.stopTransferLoop();
}

void UacDevice::recordPCM(unsigned char * data, size_t size)
{
    device.receiveIsoData(AUDIO_INPUT_STREAMING_EP,
                           data,
                           size,
                           INPUT_PACKET_SIZE);
}

void UacDevice::loopback()
{
    device.loopback(AUDIO_INPUT_STREAMING_EP, INPUT_PACKET_SIZE, AUDIO_OUTPUT_STREAMING_EP, OUTPUT_PACKET_SIZE);
}

void UacDevice::loopback(uint16_t inpPacketSize, uint16_t outPacketSize)
{
    device.loopback(AUDIO_INPUT_STREAMING_EP, inpPacketSize, AUDIO_OUTPUT_STREAMING_EP, outPacketSize);
}
