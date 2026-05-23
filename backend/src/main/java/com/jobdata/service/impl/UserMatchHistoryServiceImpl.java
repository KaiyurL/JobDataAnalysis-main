package com.jobdata.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.entity.UserMatchHistory;
import com.jobdata.mapper.UserMatchHistoryMapper;
import com.jobdata.service.UserMatchHistoryService;
import org.springframework.stereotype.Service;

@Service
public class UserMatchHistoryServiceImpl extends ServiceImpl<UserMatchHistoryMapper, UserMatchHistory> implements UserMatchHistoryService {
}
