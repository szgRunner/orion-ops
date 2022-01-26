package com.orion.ops.aspect;

import com.orion.ops.annotation.EventLog;
import com.orion.ops.consts.event.EventKeys;
import com.orion.ops.consts.event.EventParamsHolder;
import com.orion.ops.entity.dto.UserDTO;
import com.orion.ops.service.api.UserEventLogService;
import com.orion.ops.utils.Currents;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 用户操作日志切面
 *
 * @author Jiahang Li
 * @version 1.0.0
 * @since 2022/1/22 17:47
 */
@Component
@Aspect
@Slf4j
@Order(20)
public class UserEventLogAspect {

    @Resource
    private UserEventLogService userEventLogService;

    @Pointcut("@annotation(e)")
    public void eventLogPoint(EventLog e) {
    }

    @Before(value = "eventLogPoint(e)", argNames = "e")
    public void beforeLogRecord(EventLog e) {
        EventParamsHolder.addParam(EventKeys.INNER_REQUEST_SEQ, LogAspect.SEQ_HOLDER.get());
        // 有可能是登陆接口有可能为空 则用内部常量策略
        UserDTO user = Currents.getUser();
        if (user != null) {
            EventParamsHolder.addParam(EventKeys.INNER_USER_ID, user.getId());
            EventParamsHolder.addParam(EventKeys.INNER_USER_NAME, user.getUsername());
        }
    }

    @AfterReturning(pointcut = "eventLogPoint(e)", argNames = "e")
    public void afterLogRecord(EventLog e) {
        userEventLogService.recordLog(e.value(), true);
    }

    @AfterThrowing(pointcut = "eventLogPoint(e)", argNames = "e")
    public void afterLogRecordThrowing(EventLog e) {
        userEventLogService.recordLog(e.value(), false);
    }

}
