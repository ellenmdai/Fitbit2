#include <Wire.h>
#include <SFE_MMA8452Q.h>
MMA8452Q accel;

enum State {
  pedometer,
  sleep,
};

int stepCount = 0;
int crossCount = 0;
const float STEP_THRESHOLD = 1.1;
unsigned long sleepTime = 0;  // in seconds
unsigned long lastReadTime = 0;
const float SLEEP_XY_THRESHOLD = 0.2;
const float SLEEP_Z_THRESHOLD = 0.2;
const int FILTER_COUNTS = 7;
float temps[FILTER_COUNTS];
int tempCount = 0;


State currentState = pedometer;
float lastX = 0;
float lastY = 0;
float lastZ = 0;

// for debouncing
int lastResetReading = HIGH;
int resetState = HIGH;
int lastSwitchReading = HIGH;
int switchState = HIGH;
unsigned long lastDebounceTime = 0;
int debounceDelay = 50;
unsigned long lastTempReadTime = 0;

void setup() {
  accel.init();
  pinMode(2, INPUT_PULLUP); // switching button
  pinMode(3, INPUT_PULLUP); // reset
  pinMode(13, OUTPUT);
  Serial.begin(9600);
  Serial.println("setup finished");
}

void loop() {
  // the fitbit sends data over every 200 milliseconds in pedometer mode
  // and every 1 second in sleep mode, and always sends temperature
  // data every second.
  unsigned long now = millis();
  switch (currentState) {
    case pedometer:
      if (now - lastReadTime >= 200) {
        sendAccelX();
        sendAccelY();
        sendAccelZ();
        countSteps();
        lastReadTime = now;
      }  
      break;
    case sleep:
      if (now - lastReadTime >= 1000) {
        trackSleep();
        lastReadTime = now;
      }
      break;
  }
  if (now - lastTempReadTime >= 1000) {
    sendTimestamp(now);
    sendFilTemp(analogRead(0));
    lastTempReadTime = now;
  }
  checkReset();
  checkStateSwitch();
}

// reads Z axis data to tell if a step has been taken.
void countSteps() {
  if (accel.available()) {
    accel.read();
    if (accel.cz >= STEP_THRESHOLD && lastZ < STEP_THRESHOLD)
      crossCount++;
    else if (accel.cz < STEP_THRESHOLD && lastZ >= STEP_THRESHOLD)
      crossCount++;
    if (crossCount == 2) {
      stepCount++;
      crossCount = 0;
//      Serial.print(stepCount);
//      Serial.println(" steps");
      sendStepCount();
      sendTimestamp(millis());
    }
    lastZ = accel.cz;
  }
}

// Checks if there is minimal movement in all directions to track sleep.
void trackSleep() {
  if (accel.available()) {
    accel.read();
    if (abs(accel.cx - lastX) < SLEEP_XY_THRESHOLD
        && abs(accel.cy - lastY) < SLEEP_XY_THRESHOLD
        && abs(accel.cz - lastZ) < SLEEP_Z_THRESHOLD) {
      sleepTime++; 
//      Serial.print(sleepTime);
//      Serial.println(" seconds of sleep!");    
    }
    sendTimestamp(millis()); 
    sendSleepTime();
    lastX = accel.cx;
    lastY = accel.cy;
    lastZ = accel.cz;

  }
}

void checkStateSwitch() {
  int reading = digitalRead(2);
  if (lastSwitchReading != reading) {
    lastDebounceTime = millis();
  }
  if ((millis()-lastDebounceTime) > debounceDelay) {
    if (reading != switchState) {
      switchState = reading;
      if (switchState == LOW) {
        switch (currentState) {
          case pedometer:
            digitalWrite(13, HIGH);
            currentState = sleep; 
            break;
          case sleep:
            digitalWrite(13, LOW);
            currentState = pedometer;
            break; 
        }
      }
    }
  }
  lastSwitchReading = reading;
}

void checkReset() {
  int reading = digitalRead(3);
  if (lastResetReading != reading) {
    lastDebounceTime = millis();
  }
  if ((millis()-lastDebounceTime) > debounceDelay) {
    if (reading != resetState) {
      resetState = reading;
      if (resetState == LOW) {
        switch (currentState) {
          case pedometer:
            stepCount = 0;
            crossCount = 0;
            sendDebug("Pedometer reset");
            sendStepCount();
            sendTimestamp(millis());
            sendPedReset();
            break;
          case sleep:
            sleepTime = 0;
            sendDebug("Sleep timer reset");
            sendSleepTime();
            sendSleepReset();
            break; 
        }
      }
    }
  }
  lastResetReading = reading;
}

void sendStepCount() {
  Serial.write('!');
  Serial.write(0x37);
  Serial.write(stepCount >> 8);
  Serial.write(stepCount);
}

void sendSleepTime() {
  Serial.write('!');
  Serial.write(0x38);
  Serial.write(sleepTime >> 24);
  Serial.write(sleepTime >> 16);
  Serial.write(sleepTime >> 8);
  Serial.write(sleepTime);
}

void sendAccelX() {
  unsigned long rawBits;
  rawBits = *(unsigned long *) &accel.cx;
  Serial.write('!');
  Serial.write(0x39);
  Serial.write(rawBits >> 24);
  Serial.write(rawBits >> 16);
  Serial.write(rawBits >> 8);
  Serial.write(rawBits);
}

void sendAccelY() {
  unsigned long rawBits;
  rawBits = *(unsigned long *) &accel.cy;
  Serial.write('!');
  Serial.write(0x3a);
  Serial.write(rawBits >> 24);
  Serial.write(rawBits >> 16);
  Serial.write(rawBits >> 8);
  Serial.write(rawBits);
}

void sendAccelZ() {
  unsigned long rawBits;
  rawBits = *(unsigned long *) &accel.cz;
  Serial.write('!');
  Serial.write(0x3b);
  Serial.write(rawBits >> 24);
  Serial.write(rawBits >> 16);
  Serial.write(rawBits >> 8);
  Serial.write(rawBits);
}

void sendPedReset() {
   Serial.write('!');
  Serial.write(0x3c);
}

void sendSleepReset() {
   Serial.write('!');
  Serial.write(0x3d);
}

// first filters the temeperature using a rolling average,
// then converts float to long to transfer to Java program.
void sendFilTemp(int analog) {
  float reading = (analog * (5.0/1024.0)*100) - 50;
  temps[tempCount % FILTER_COUNTS] = reading;
  tempCount = tempCount + 1;
  float filtered = 0.0;
  for (int i = 0; i < FILTER_COUNTS; i++) {
    filtered += temps[i];
  }
  filtered = filtered / FILTER_COUNTS;
  unsigned long rawBits;
  rawBits = *(unsigned long *) &filtered;
  Serial.write('!');
  Serial.write(0x36);
  Serial.write(rawBits >> 24);
  Serial.write(rawBits >> 16);
  Serial.write(rawBits >> 8);
  Serial.write(rawBits);
}

void sendTimestamp(unsigned long mil) {
    Serial.write('!');
    Serial.write(0x32);
    Serial.write(mil >> 24);
    Serial.write(mil >> 16);
    Serial.write(mil >> 8);
    Serial.write(mil); 
}

void sendDebug(char *value) {
  Serial.write('!');
  Serial.write('0x30');
  int size = strlen(value);
  int sizeLeft = size >> 8;
  Serial.write(sizeLeft);
  Serial.write(size);
  Serial.write(value);
}

void sendError(char *value) {
  Serial.write('!');
  Serial.write('0x31');
  int size = strlen(value);
  int sizeLeft = size >> 8;
  Serial.write(sizeLeft);
  Serial.write(size);
  Serial.write(value);
}
