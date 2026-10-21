package com.titran.pingcortex.dto.response;

import com.titran.pingcortex.model.User;

public record LoginResult(String accessToken, String rawRefreshToken, UserResponse user) {
}
