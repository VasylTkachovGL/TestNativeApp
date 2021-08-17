#include "UsbDevice.h"
#include "Utils.h"
#include "log.h"

#include <libusb.h>
#include <cstdio>
#include <jni.h>

#define TAG "UsbDevice"
static const size_t NUM_TRANSFERS = 10;
static const uint8_t NUM_PACKETS = 1;

UsbDevice::UsbDevice(jint fd) {
    // Init the library
    libusb_set_option(nullptr, LIBUSB_OPTION_WEAK_AUTHORITY);
    int ret = libusb_init(nullptr);
    check(ret, "libusb_init()");

    // The Android system owns the USB devices, and we can only ask the OS to do USB operations.
    // to do so, Android gives us a file descriptor we can do I/O operations on to perform USB read/writes.
    // opening and closing of this device must be done by the Java layer. we can call libusb_close(),
    // and in fact we should once we're done with the native handle, but we're not allowed to call
    // libusb_open(). instead, we use libusb_wrap_sys_device() to open the file descriptor that Java
    // gave us.
    libusb_wrap_sys_device(nullptr, fd, &hdev);
    check(hdev != nullptr, "open_device_with_vid_pid");

    // Allocate needed number of transfer objects
    for (size_t i = 0; i < NUM_TRANSFERS; i++) {
        libusb_transfer *xfer = libusb_alloc_transfer(NUM_PACKETS);
        availableXfers.push_back(xfer);

        libusb_transfer *xfer2 = libusb_alloc_transfer(NUM_PACKETS);
        availableOutXfers.push_back(xfer2);

        // TODO: gather in and out transfers, along with associated buffer in a single structure
        auto *buf = new uint8_t[1024 * NUM_PACKETS];  // Size of the buffer shall correspond input and output packet sizes, multiplied by NUM_TRANSFERS
        buffers.push_back(buf);
        // we need double number of buffers to handle simultaneous input and output
        buf = new uint8_t[1024 * NUM_PACKETS];  // Size of the buffer shall correspond input and output packet sizes, multiplied by NUM_TRANSFERS
        buffers.push_back(buf);
    }
}

UsbDevice::~UsbDevice() {
    for (libusb_transfer *xfer : availableXfers)
        libusb_free_transfer(xfer);

    for (libusb_transfer *xfer : availableOutXfers)
        libusb_free_transfer(xfer);

    for (uint8_t *buf : buffers)
        delete[] buf;

//    if (loopbackThread != nullptr && loopbackThread->joinable()) {
        loopbackThread->join();
        delete loopbackThread;
//    }

    libusb_close(hdev);
    libusb_exit(nullptr);
}

void UsbDevice::openInterface(uint8_t interface) {
    // Detach kernel driver if needed (so that we can operate instead of the driver)
    int ret = libusb_kernel_driver_active(hdev, interface);
    check(ret, "libusb_kernel_driver_active()");
    if (ret == 1) {
        ret = libusb_detach_kernel_driver(hdev, interface);
        check(ret, "libusb_detach_kernel_driver()");
    }

    // Now claim the interface
    ret = libusb_claim_interface(hdev, interface);
    check(ret, "libusb_claim_interface()");
}

void UsbDevice::closeInterface(uint8_t interface) {
    int ret = libusb_release_interface(hdev, interface);
    check(ret, "libusb_release_interface()");
}

void UsbDevice::setAltsetting(uint8_t interface, uint8_t altsetting) {
    int ret = libusb_set_interface_alt_setting(hdev, interface, altsetting);
    check(ret, "libusb_set_interface_alt_setting()");
}

void UsbDevice::controlReq(uint8_t requestType, uint8_t bRequest, uint16_t wValue, uint16_t wIndex, uint16_t wLength, unsigned char *data) {
    int ret = libusb_control_transfer(hdev, requestType, bRequest, wValue, wIndex, data, wLength,
                                      1000);
    check(ret, "libusb_control_transfer()");
}

void UsbDevice::getControlAttr(bool recepient, uint8_t bRequest, uint16_t wValue, uint16_t wIndex, uint16_t wLength, unsigned char *data) {
    uint8_t receiver = recepient ? LIBUSB_RECIPIENT_ENDPOINT : LIBUSB_RECIPIENT_INTERFACE;
    uint8_t bmRequestType = LIBUSB_ENDPOINT_IN | LIBUSB_REQUEST_TYPE_CLASS | receiver;
    controlReq(bmRequestType, bRequest, wValue, wIndex, wLength, data);
}

void UsbDevice::setControlAttr(bool recepient, uint8_t bRequest, uint16_t wValue, uint16_t wIndex, uint16_t wLength, unsigned char *data) {
    uint8_t receiver = recepient ? LIBUSB_RECIPIENT_ENDPOINT : LIBUSB_RECIPIENT_INTERFACE;
    uint8_t bmRequestType = LIBUSB_ENDPOINT_OUT | LIBUSB_REQUEST_TYPE_CLASS | receiver;
    controlReq(bmRequestType, bRequest, wValue, wIndex, wLength, data);
}

void UsbDevice::transferCompleteCB(struct libusb_transfer *xfer) {
    auto *device = static_cast<UsbDevice *>(xfer->user_data);
    device->handleTransferCompleteCB(xfer);
}

void UsbDevice::handleTransferCompleteCB(libusb_transfer *xfer) {
    availableXfers.push_back(xfer);
}

void UsbDevice::sendIsoData(uint8_t ep, unsigned char *data, size_t size, uint16_t packetSize) {
    size_t totalPackets = size / packetSize;
    size_t bytesToGo = size;

    while (bytesToGo > 0) {
        // Feed as many packets as possible
        while (availableXfers.size() > 0) {
            size_t chunkSize = std::min((size_t) packetSize * NUM_PACKETS, bytesToGo);

            libusb_transfer *xfer = availableXfers.back();
            availableXfers.pop_back();
            libusb_fill_iso_transfer(xfer, hdev, ep, data, chunkSize, NUM_PACKETS, transferCompleteCB, this, 1000);
            libusb_set_iso_packet_lengths(xfer, packetSize);
            libusb_submit_transfer(xfer);

            data += chunkSize;
            bytesToGo -= chunkSize;
        }

        int ret = libusb_handle_events(nullptr);
        check(ret, "libusb_handle_events()");
    }

    // Wait for remaining packets to be sent
//    timeval timeout = {1, 0};
//    while (availableXfers.size() != NUM_TRANSFERS) {
//        LOG_D(TAG, "Handle events for available packets");
//        int ret = libusb_handle_events(nullptr);
//        //int ret = libusb_handle_events_timeout(nullptr, &timeout);
//        check(ret, "libusb_handle_events()");
//    }
}

void UsbDevice::receiveIsoData(uint8_t ep, unsigned char *data, size_t size, uint16_t packetSize) {
    size_t totalPackets = size / packetSize;
    size_t bytesToGo = size;

    while (bytesToGo > 0) {
        // Schedule as many packet transfers as possible
        while (availableXfers.size() > 0) {
            size_t chunkSize = std::min((size_t) packetSize * NUM_PACKETS, bytesToGo);

            libusb_transfer *xfer = availableXfers.back();
            availableXfers.pop_back();

            libusb_fill_iso_transfer(xfer, hdev, ep, data, chunkSize, NUM_PACKETS, transferCompleteCB, this, 1000);
            libusb_set_iso_packet_lengths(xfer, packetSize);
            libusb_submit_transfer(xfer);

            data += chunkSize;
            bytesToGo -= chunkSize;
        }

        int ret = libusb_handle_events(nullptr);
        check(ret, "libusb_handle_events()");
    }

    // Wait for remaining packets to be sent
    while (availableXfers.size() != NUM_TRANSFERS) {
        int ret = libusb_handle_events(nullptr);
        check(ret, "libusb_handle_events()");
    }
}

void UsbDevice::loopback(uint8_t inEp, uint16_t inPacketSize, uint8_t outEp, uint16_t outPacketSize)
{
    this->inEp = inEp;
    this->inPacketSize = inPacketSize;
    this->outEp = outEp;
    this->outPacketSize = outPacketSize;

    auto loopbackFunc = [this]() {this->loopbackEventLoop();};
    loopbackThread = new std::thread(loopbackFunc);
}

void UsbDevice::loopbackEventLoop()
{
    while(true)
    {
        // Schedule as many packet transfers as possible
        while(availableXfers.size() > 0)
        {
            size_t chunkSize = inPacketSize * NUM_PACKETS;

            libusb_transfer * xfer = availableXfers.back();
            availableXfers.pop_back();

            uint8_t * buf = buffers.back();
            buffers.pop_back();

            libusb_fill_iso_transfer(xfer, hdev, inEp, buf, chunkSize, NUM_PACKETS, loopbackPacketReceiveCB, this, 1000);
            libusb_set_iso_packet_lengths(xfer, inPacketSize);
            libusb_submit_transfer(xfer);
        }

        int ret = libusb_handle_events(NULL);
        check(ret, "libusb_handle_events()");
    }
}

void UsbDevice::loopbackPacketReceiveCB(libusb_transfer * xfer)
{
    UsbDevice * device = static_cast<UsbDevice*>(xfer->user_data);
    device->handleLoopbackPacketReceive(xfer);
}

void UsbDevice::handleLoopbackPacketReceive(libusb_transfer * xfer)
{
    // Skip this transfer if there is no output transfers available
    if(buffers.size() == 0 || availableOutXfers.size() == 0)
    {
    // return input transfer and its buffer to the pool
        buffers.push_back(xfer->buffer);
        availableXfers.push_back(xfer);
        return;
    }

    // Prepare output buffer and transfer
    uint8_t * outputBuf = buffers.back();
    buffers.pop_back();
    libusb_transfer * outXfer = availableOutXfers.back();
    availableOutXfers.pop_back();

    libusb_fill_iso_transfer(outXfer, hdev, outEp, outputBuf, 0, NUM_PACKETS, loopbackPacketSendCB, this, 1000);

    // Iterate over the packets and Convert 24bit mono to 16bit stereo
    int totalOutputLen = 0;
    for(int p = 0; p < xfer->num_iso_packets; p++)
    {
        uint8_t * inputBuf = libusb_get_iso_packet_buffer(xfer, p);
        int inputLen = xfer->iso_packet_desc[p].actual_length;
        int outputLen = 0;
        for(int i=0; i<inputLen/3; i++)
        {
            int16_t v = *(int16_t *)(inputBuf + i*3 + 1);

            *(int16_t *)(outputBuf + totalOutputLen + i*4) = v;
            *(int16_t *)(outputBuf + totalOutputLen + i*4 + 2) = v;
            outputLen += 4;
        }
//        for(int i=0; i<inputLen/3; i++)
//        {
//            int16_t v = *(int16_t *)(inputBuf + i*3 + 1);
//
//            *(int16_t *)(outputBuf + totalOutputLen + i*2) = v;
//            outputLen += 2;
//        }

        outXfer->iso_packet_desc[p].length = outputLen;

        totalOutputLen += outputLen;
    }

    outXfer->length = totalOutputLen;


    // return input transfer and its buffer to the pool
    buffers.push_back(xfer->buffer);
    availableXfers.push_back(xfer);

    // Schedule output transfer
    libusb_submit_transfer(outXfer);
}

void UsbDevice::loopbackPacketSendCB(libusb_transfer * xfer)
{
    UsbDevice * device = static_cast<UsbDevice*>(xfer->user_data);
    device->handleLoopbackPacketSend(xfer);
}

void UsbDevice::handleLoopbackPacketSend(libusb_transfer * xfer)
{
    //return output transfer and its buffer to the pool
    buffers.push_back(xfer->buffer);
    availableOutXfers.push_back(xfer);
}

