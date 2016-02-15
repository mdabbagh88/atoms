# Atoms [![Build Status](https://travis-ci.org/atomsd/atoms.svg?branch=master)](https://travis-ci.org/atomsd/atoms)
The _Atoms_ is a free and open source mobile application server that allows sending push notifications to different (mobile) platforms and has support for:
* [Apple’s APNs](http://developer.apple.com/library/mac/#documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW9)
* [Google Cloud Messaging (GCM)](http://developer.android.com/google/gcm/index.html)
* [Microsoft's Windows Push Notification service (WNS)](https://msdn.microsoft.com/en-us/library/windows/apps/hh913756.aspx)
* [Microsoft's Push Notification service (MPNs)](http://msdn.microsoft.com/en-us/library/windows/apps/ff402558.aspx)
* [Amazon Device Messaging (ADM)](https://developer.amazon.com/appsandservices/apis/engage/device-messaging/)
* [Mozilla’s SimplePush](https://wiki.mozilla.org/WebAPI/SimplePush).

_Atoms_ releases additional functionality while maintaining _AeroGear_ API compatibility:
* [Full-stack](http://atomsd.org/downloads) rpm/deb installers across a variety of platforms (RHEL, Debian, Fedora, Ubuntu).
* SSL Suuport, embeded NGINX, embeded postgresql.
* Centralized configuration/managment. 
* Code base registraion verification - [SMS/Email Verification process](http://atomsd.org/features/).
* Store & forward JSON documents.
* Silent Push Notifications (Notification without payload) 

<img src="https://raw.githubusercontent.com/aerogear/aerogear-unifiedpush-server/master/ups-ui-screenshot.png" height="427px" width="550px" />

## Project Info

|                 | Project Info  |
| --------------- | ------------- |
| License:        | Apache License, Version 2.0  |
| Build:          | Maven  |
| Documentation:  | http://atomsd.org/documentation/  |
| Issue tracker:  | https://github.com/atomsd/atoms/issues  |

## Getting started

For the on-premise version, execute the following steps to get going!

* Download the [latest package (rpm/deb) files](http://atomsd.org/downloads)
* Or follow the steps on the [install page](http://atomsd.org/documentation/)
* Run ``sudo atoms-ctl reconfigure``
* Start the server ``sudo atoms-ctl start``

Now go to ``http://localhost/atoms`` and enjoy the ATOMS Server.
__NOTE:__ the default user/password is ```admin```:```123```

## Documentation

For more details about the current release, please consult [our documentation] (http://atomsd.org/documentation/)

#### Generate REST Documentation

Up to date generated REST endpoint documentation can be found in `jaxrs/target/miredot/index.html`. It is generated with every `jaxrs` module build.

## Development 

The above `Getting started` section covers the latest release of the ATOMS Server. For development and deploying `SNAPSHOT` versions, you will find information in this section.

### Deployment 

For deployment of the `master branch` to a specific server (Wildfly 8.2.1), you need to build the WAR files and deploy them to a running and configured server.

First build the entire project:
```
mvn clean install
```

## Deprecation Notices

###  1.1.0

*Chrome Packaged Apps*

The Chrome Packaged App Variant will be removed.  Google has deprecated the [chrome.pushMessaging API](https://developer.chrome.com/extensions/pushMessaging) in favor of the [chrome.gcm API](https://developer.chrome.com/extensions/gcm).

This change allows the Atoms Server to now use the Android Variant for both Android and Chrome Apps.

If you are using this functionality, please convert your applications to use the new API and recreate your variants.

## Found a bug?

If you found a bug please create a ticket for us on [Issues](https://github.com/atomsd/atoms/issues) with some steps to reproduce it.
