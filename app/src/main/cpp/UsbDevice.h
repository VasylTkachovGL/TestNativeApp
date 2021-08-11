#ifndef _USB_DEVICE_H
#define _USB_DEVICE_H

#include <stdint.h>
#include <vector>
#include <libusb.h>
#include <jni.h>

struct libusb_device_handle;
struct libusb_transfer;

class UsbDevice
{
    std::vector<libusb_transfer *>  availableXfers;

public:
    UsbDevice(jint fd);
    ~UsbDevice();

    int openDevice(uint32_t fd);
    void openInterface(uint8_t interface);
    void setAltsetting(uint8_t interface, uint8_t altsetting);

    void controlReq(uint8_t requestType, uint8_t bRequest, uint16_t wValue, uint16_t wIndex, uint16_t wLength, unsigned char * data);

    void getControlAttr(bool recepient, uint8_t bRequest, uint16_t wValue, uint16_t wIndex, uint16_t wLength, unsigned char * data);
    void setControlAttr(bool recepient, uint8_t bRequest, uint16_t wValue, uint16_t wIndex, uint16_t wLength, unsigned char * data);

    void sendIsoData(uint8_t ep, unsigned char * data, size_t size, uint16_t packetSize);
    void receiveIsoData(uint8_t ep, unsigned char * data, size_t size, uint16_t packetSize);
    void loopback(uint8_t epIn, uint8_t epOut, unsigned char * data, unsigned char * dataOut, size_t size, uint16_t packetSizeIn, uint16_t packetSizeOut);

    void closeInterface(uint8_t interface);

protected:
    static void transferCompleteCB(libusb_transfer * xfer);
    virtual void handleTransferCompleteCB(libusb_transfer * xfer);

private:
    libusb_context *m_context;
    libusb_device_handle * hdev;
};

#endif //_USB_DEVICE_H
