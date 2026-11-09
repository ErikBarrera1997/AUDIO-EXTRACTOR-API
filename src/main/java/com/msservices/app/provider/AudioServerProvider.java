package com.msservices.app.provider;

import com.msservices.app.dto.AudioSearchResultDto;
import com.msservices.app.dto.ExtractedAudioDto;
import java.util.List;

// A backend that can search and extract audio. Adding another backend means adding a Spring bean
// that implements this interface; the controller and the service stay unchanged and the registry
// exposes it automatically.
public interface AudioServerProvider {

    String id();

    String displayName();

    boolean supportsUserCookies();

    default List<String> aliases() {
        return List.of();
    }

    List<AudioSearchResultDto> search(String videoName, String rawCookies);

    ExtractedAudioDto extract(String videoName, String videoId, String rawCookies);
}
