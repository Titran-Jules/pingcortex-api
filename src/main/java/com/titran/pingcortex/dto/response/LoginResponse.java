package com.titran.pingcortex.dto.response;

import com.titran.pingcortex.model.User;

public record LoginResponse(String accessToken, String refreshToken, UserResponse user) {
}
