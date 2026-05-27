package com.jobdata.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.entity.UserMatchHistory;
import com.jobdata.mapper.UserMatchHistoryMapper;
import com.jobdata.service.UserMatchHistoryService;
import org.springframework.stereotype.Service;

/**
 * 用户匹配历史服务实现：基于 MyBatis-Plus 的通用 ServiceImpl 提供 CRUD 能力。
 */
@Service
public class UserMatchHistoryServiceImpl extends ServiceImpl<UserMatchHistoryMapper, UserMatchHistory> implements UserMatchHistoryService {
}
