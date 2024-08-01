package com.orion.ops.controller;

import com.orion.lang.define.wrapper.DataGrid;
import com.orion.lang.define.wrapper.HttpWrapper;
import com.orion.ops.annotation.DemoDisableApi;
import com.orion.ops.annotation.EventLog;
import com.orion.ops.annotation.RestWrapper;
import com.orion.ops.constant.Const;
import com.orion.ops.constant.MessageConst;
import com.orion.ops.constant.event.EventType;
import com.orion.ops.constant.machine.MachineAuthType;
import com.orion.ops.entity.request.machine.MachineInfoRequest;
import com.orion.ops.entity.vo.machine.MachineInfoVO;
import com.orion.ops.service.api.MachineInfoService;
import com.orion.ops.utils.Valid;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 机器信息 api
 *
 * @author Jiahang Li
 * @version 1.0.0
 * @since 2021/4/3 21:11
 */
@Api(tags = "机器信息")
@RestController
@RestWrapper
@RequestMapping("/orion/api/machine")
public class MachineInfoController {

    @Resource
    private MachineInfoService machineInfoService;

    @DemoDisableApi
    @PostMapping("/add")
    @ApiOperation(value = "添加机器")
    @EventLog(EventType.ADD_MACHINE)
    public Long add(@RequestBody MachineInfoRequest request) {
        this.check(request);
        MachineAuthType machineAuthTypeEnum = Valid.notNull(MachineAuthType.of(request.getAuthType()));
        if (MachineAuthType.PASSWORD.equals(machineAuthTypeEnum)) {
            Valid.notBlank(request.getPassword());
        }
        return machineInfoService.addMachine(request);
    }

    @DemoDisableApi
    @PostMapping("/update")
    @ApiOperation(value = "修改机器")
    @EventLog(EventType.UPDATE_MACHINE)
    public int update(@RequestBody MachineInfoRequest request) {
        Valid.notNull(request.getId());
        this.check(request);
        return machineInfoService.updateMachine(request);
    }

    @DemoDisableApi
    @PostMapping("/delete")
    @ApiOperation(value = "删除机器")
    @EventLog(EventType.DELETE_MACHINE)
    public Integer delete(@RequestBody MachineInfoRequest request) {
        List<Long> idList = Valid.notEmpty(request.getIdList());
        // 设置日志参数
        return machineInfoService.deleteMachine(idList);
    }

    @DemoDisableApi
    @PostMapping("/update-status")
    @ApiOperation(value = "停用/启用机器")
    @EventLog(EventType.CHANGE_MACHINE_STATUS)
    public Integer status(@RequestBody MachineInfoRequest request) {
        List<Long> idList = Valid.notEmpty(request.getIdList());
        Integer status = Valid.notNull(request.getStatus());
        Valid.in(status, Const.ENABLE, Const.DISABLE);
        return machineInfoService.updateStatus(idList, status);
    }

    @PostMapping("/list")
    @ApiOperation(value = "获取机器列表")
    public DataGrid<MachineInfoVO> list(@RequestBody MachineInfoRequest request) {
        return machineInfoService.listMachine(request);
    }

    @PostMapping("/detail")
    @ApiOperation(value = "获取机器详情")
    public MachineInfoVO detail(@RequestBody MachineInfoRequest request) {
        Long id = Valid.notNull(request.getId());
        return machineInfoService.machineDetail(id);
    }

    @DemoDisableApi
    @PostMapping("/copy")
    @ApiOperation(value = "复制机器")
    @EventLog(EventType.COPY_MACHINE)
    public Long copy(@RequestBody MachineInfoRequest request) {
        Long id = Valid.notNull(request.getId());
        return machineInfoService.copyMachine(id);
    }

    @PostMapping("/test-ping")
    @ApiOperation(value = "尝试ping机器")
    public HttpWrapper<?> ping(@RequestBody MachineInfoRequest request) {
        Long id = Valid.notNull(request.getId());
        machineInfoService.testPing(id);
        return HttpWrapper.ok();
    }

    @PostMapping("/test-connect")
    @ApiOperation(value = "尝试连接机器")
    public HttpWrapper<?> connect(@RequestBody MachineInfoRequest request) {
        Long id = Valid.notNull(request.getId());
        machineInfoService.testConnect(id);
        return HttpWrapper.ok();
    }

    @PostMapping("/direct-test-ping")
    @ApiOperation(value = "直接尝试ping机器")
    public HttpWrapper<?> directPing(@RequestBody MachineInfoRequest request) {
        String host = Valid.notBlank(request.getHost());
        machineInfoService.testPing(host);
        return HttpWrapper.ok();
    }

    @PostMapping("/direct-test-connect")
    @ApiOperation(value = "直接尝试连接机器")
    public HttpWrapper<?> directConnect(@RequestBody MachineInfoRequest request) {
        Valid.allNotBlank(request.getHost(), request.getUsername());
        Integer sshPort = Valid.notNull(request.getSshPort());
        Valid.inRange(sshPort, 2, 65534, MessageConst.ABSENT_PARAM);
        MachineAuthType authType = Valid.notNull(MachineAuthType.of(request.getAuthType()));
        if (MachineAuthType.PASSWORD.equals(authType)) {
            Valid.notBlank(request.getPassword());
        } else if (MachineAuthType.SECRET_KEY.equals(authType)) {
            Valid.notNull(request.getKeyId());
        }
        machineInfoService.testConnect(request);
        return HttpWrapper.ok();
    }

    /**
     * 合法校验
     */
    private void check(MachineInfoRequest request) {
        Valid.notBlank(request.getHost());
        Integer sshPort = Valid.notNull(request.getSshPort());
        Valid.inRange(sshPort, 2, 65534, MessageConst.ABSENT_PARAM);
        Valid.notBlank(request.getName());
        Valid.notBlank(request.getTag());
        Valid.notBlank(request.getUsername());
    }

}
