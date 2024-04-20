package com.codegym.finwallet.service.impl;

import com.codegym.finwallet.constant.AuthConstant;
import com.codegym.finwallet.constant.UserConstant;
import com.codegym.finwallet.dto.CommonResponse;
import com.codegym.finwallet.dto.payload.request.ProfileRequest;
import com.codegym.finwallet.entity.Profile;
import com.codegym.finwallet.repository.ProfileRepository;
import com.codegym.finwallet.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {
    private final ProfileRepository profileRepository;
    private final ModelMapper modelMapper;

    @Override
    public Profile findProfileByEmail(String email) {
        Optional<Profile> profile = profileRepository.findProfileByEmail(email);
        return  profile.orElseThrow(() -> new RuntimeException(UserConstant.FIND_PROFILE_FAIL + email));
    }

    @Override
    public void saveProfile(Profile profile) {
        profileRepository.save(profile);
    }

    @Override
    public CommonResponse updateProfile(ProfileRequest request) {
        CommonResponse commonResponse = new CommonResponse();
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            Profile curentProfile = findProfileByEmail(email);
            Profile newProfile = modelMapper.map(request, curentProfile.getClass());
            newProfile.setId(curentProfile.getId());
            profileRepository.save(newProfile);
            commonResponse = commonResponse.builder()
                    .data(null)
                    .message("Update profile successful")
                    .status(HttpStatus.OK)
                    .build();
        }catch (SecurityException e){
            commonResponse = commonResponse.builder()
                    .data(null)
                    .message("Can't update profile")
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }

        return commonResponse;
    }
}
