package com.jobdata.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.ai.context.UserContextHolder;
import com.jobdata.entity.UserFavoriteJob;
import com.jobdata.entity.UserProfile;
import com.jobdata.service.UserFavoriteJobService;
import com.jobdata.service.UserJobHistoryService;
import com.jobdata.service.UserProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserToolsTest {

    @Mock
    UserProfileService userProfileService;

    @Mock
    UserFavoriteJobService userFavoriteJobService;

    @Mock
    UserJobHistoryService userJobHistoryService;

    ObjectMapper objectMapper = new ObjectMapper();

    UserTools userTools;

    @BeforeEach
    void setUp() {
        UserContextHolder.setUserId(1L);
        userTools = new UserTools(userProfileService, userFavoriteJobService, userJobHistoryService, objectMapper);
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void userGetProfile_returns_empty_when_missing() {
        when(userProfileService.getOne(any())).thenReturn(null);
        Map<String, Object> out = userTools.userGetProfile();
        assertNotNull(out.get("profile"));
    }

    @Test
    void userGetProfile_parses_profile_json() {
        UserProfile row = new UserProfile();
        row.setUserId(1L);
        row.setProfileJson("{\"city\":\"北京\"}");
        row.setResumeMetaJson("{\"file\":\"a.pdf\"}");
        row.setUpdatedAt(LocalDateTime.now());
        when(userProfileService.getOne(any())).thenReturn(row);

        Map<String, Object> out = userTools.userGetProfile();
        Map<String, Object> profile = (Map<String, Object>) out.get("profile");
        assertEquals("北京", String.valueOf(profile.get("city")));
    }

    @Test
    void userListFavorites_maps_basic_fields() {
        UserFavoriteJob fav = new UserFavoriteJob();
        fav.setId(1L);
        fav.setUserId(1L);
        fav.setSourceTable("job_info_51job");
        fav.setJobUrl("http://x");
        fav.setJobName("数据分析");
        when(userFavoriteJobService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<UserFavoriteJob>>any()))
                .thenReturn(List.of(fav));

        List<Map<String, Object>> out = userTools.userListFavorites(10);
        assertEquals(1, out.size());
        assertEquals("数据分析", String.valueOf(out.get(0).get("jobName")));
    }
}
