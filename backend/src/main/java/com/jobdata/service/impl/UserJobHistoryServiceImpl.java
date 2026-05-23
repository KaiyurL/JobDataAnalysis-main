package com.jobdata.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.entity.UserJobHistory;
import com.jobdata.mapper.UserJobHistoryMapper;
import com.jobdata.service.UserJobHistoryService;
import org.springframework.stereotype.Service;

@Service
public class UserJobHistoryServiceImpl extends ServiceImpl<UserJobHistoryMapper, UserJobHistory> implements UserJobHistoryService {
}
