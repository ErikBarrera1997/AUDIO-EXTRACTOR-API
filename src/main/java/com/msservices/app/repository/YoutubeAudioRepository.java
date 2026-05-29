package com.msservices.app.repository;

import com.msservices.app.dto.ExtractedAudioDto;

public interface YoutubeAudioRepository {

    ExtractedAudioDto extractAudioByVideoName(String videoName);
}
