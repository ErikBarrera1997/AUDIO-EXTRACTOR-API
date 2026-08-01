package com.msservices.app.repository;

import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.ExtractedAudioDto;
import java.util.List;

public interface YoutubeAudioRepository {

    List<AudioSearchResultDto> searchVideos(String videoName);

    ExtractedAudioDto extractAudioByVideoName(String videoName, String videoId);
}
