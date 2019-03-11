/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _BOOTANIMATION_AUDIOPLAYER_H
#define _BOOTANIMATION_AUDIOPLAYER_H

#include <utils/Thread.h>
#include <utils/FileMap.h>
#include <utils/String8.h>

#ifdef TARGET_PLATFORM_HOMLET
#define CARD_NUM 32


struct active_pcm {
    int active;
    int periodsize;
    int periodcount;
    struct pcm *pcm;
    struct pcm *hub_pcm;
    int output_device;
};

#endif

namespace android {

class AudioPlayer : public Thread
{
public:
                AudioPlayer();
    virtual     ~AudioPlayer();
    bool        init(const char* config);

    void        playFile(FileMap* fileMap);
    void        playClip(const uint8_t* buf, int size);

private:
    virtual bool        threadLoop();
#ifdef TARGET_PLATFORM_HOMLET
    struct active_pcm	cards[CARD_NUM];
#endif

#if 0
    void                check_event(char *path);
    void                jack_state_detection();
#else
    void                jack_switch_state_detection(char *path);
#endif

private:
    int                 mCard;      // ALSA card to use
    int                 mDevice;    // ALSA device to use
    bool                mHeadsetPlugged;
    String8             mSwitchStatePath;
    int                 mPeriodSize;
    int                 mPeriodCount;
    bool                mSingleVolumeControl; // speaker volume and headphones volume is not controlled respectively
    bool                mSpeakerHeadphonesSyncOut; //speaker and headphones is not out simutaneously

    uint8_t*            mWavData;
    int                 mWavLength;
};
#ifdef TARGET_PLATFORM_HOMLET
class AudioMap
{
public:
    AudioMap();
    ~AudioMap();
    int init_audio_map(int *codec);
    int reinit_card(struct active_pcm (&cards)[CARD_NUM], int periodsize, int periodcount);
};

#endif

} // namespace android

#endif // _BOOTANIMATION_AUDIOPLAYER_H
