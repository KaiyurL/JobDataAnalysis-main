package com.jobdata.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.entity.UserJobHistory;
import com.jobdata.mapper.UserJobHistoryMapper;
import com.jobdata.service.UserJobHistoryService;
import org.springframework.stereotype.Service;

/**
 * 用户浏览历史服务实现：基于 MyBatis-Plus 的通用 ServiceImpl 提供 CRUD 能力。
 */
@Service
public class UserJobHistoryServiceImpl extends ServiceImpl<UserJobHistoryMapper, UserJobHistory> implements UserJobHistoryService {
}
