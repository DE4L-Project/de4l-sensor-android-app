# DE4L Sensing App 
![alt text][project-logo]

![Made with love in Germany](https://madewithlove.now.sh/de?heart=true&colorA=%23000000&colorB=%23299fc7&template=for-the-badge)

## Motivation

Android App for connecting [HabitatMap's AirBeam2](https://www.habitatmap.org/airbeam) sensors to an Android smartphone and transmitting values in real-time via MQTT and OAuth2 authentication. See the [App - Quickstart Guide](documentation/quickstart-airbeam2-de4l-app.pdf)

Currently the app is used in the research project [DE4L](https://de4l.io/en/about-de4l/) (Data Economy 4 Advanced Logistics) funded by the German Federal Ministry for Economic Affairs and Energy (01MD19008A).

## Google Play Store

[https://play.google.com/store/apps/details?id=io.de4l.app](https://play.google.com/store/apps/details?id=io.de4l.app)

Developed by [InfAI Management GmbH](https://infai.org/) in Leipzig, Germany.

## Features
- Real-time data connection using MQTT
- OAuth2 Token authentication for MQTT and automatic token refresh
- MQTT message buffering using Room when connection to MQTT server is lost
- If bluetooth connection to sensor is lost, reconnect procedure is initiated


## Technology Overview
- [Paho MQTT Client](https://github.com/eclipse/paho.mqtt.android)
- Okta OIDC Android [Okta OIDC Android](https://github.com/okta/okta-oidc-android) for OpenID Connect Authentication
- [Hilt](https://dagger.dev/hilt/) for Dependency Injection
- [Android Jetpack](https://developer.android.com/jetpack) (e.g. Room and Livedata)
- [Google Guava](https://github.com/google/guava)
- [GreenRobot EventBus](https://greenrobot.org/eventbus/)
- Kotlin [Coroutines](https://kotlinlang.org/docs/coroutines-guide.html) and [Flows](https://kotlinlang.org/docs/flow.html)


## Project setup

Include the following properties in your `local.properties` file and set the properties accordingly:

```
# Authentication
AUTH_DISCOVERY_URI="{DISCOVERY_URI}"                            #EXAMPLE https://auth.example.org/auth/realms/my-awesome-realm
AUTH_CLIENT_ID="{CLIENT_ID}"
AUTH_REDIRECT_URI="com.example.app:/start-app"                  #EXAMPLE VALUE
AUTH_REDIRECT_URI_END_SESSION="com.example.app:/session-ended"  #EXAMPLE VALUE
AUTH_USERNAME_CLAIM_KEY="preferred_username"                    #USE TO GET USERNAME FROM KEYCLOAK TOKEN
AUTH_MQTT_CLAIM_RESOURCE="{RESOURCE_ID}                         #USE TO ACCESS KEYCLOAK RESOURCE ROLES
AUTH_MQTT_CLAIM_ROLE="{RESOURCE_ROLE}"                          #REQUIRED RESOURCE ROLE

# MQTT
MQTT_SERVER_URL="{MQTT-BROKER-URL}"                         #EXAMPLE: ssl://broker.example.com:8883
MQTT_OAUTH_USERNAME="{MQTT_OAUTH_USERNAME}"                 #SEE NOTE BELOW
```

Note:
*Within the DE4L project [VerneMQ](https://vernemq.com/) is used as MQTT broker in combination with an authentication service which integrates into the VerneMQ authentication process by using webhooks. The authentication service provides an OAuth2 token authentication mode which is indicated by a fixed username, hence the MQTT_OAUTH_USERNAME parameter. The access token is then sent using the password parameter.*

## Upcoming
- support multiple sensors
- support more sensor types

## Known Issues (might be fixed in future releases)
- [AIRBEAM2] AirBeam2 sensor occasionally produces unparseable lines
- [PAHO] If MQTT client is connected to a mobile network and device is connected to WiFi, MQTT connection stays in mobile network

## Backend Architecture - DE4L Sensor Data Platform
![alt text][platform-architecture]


[project-logo]: documentation/logos/project-logo.png "DE4L Project Logo"
[platform-architecture]: documentation/de4l-sensor-data-platform-architecture.png "DE4L Sensor Data Platform Architecture"