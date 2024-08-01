package com.orion.ops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.orion.lang.define.wrapper.DataGrid;
import com.orion.lang.utils.Exceptions;
import com.orion.lang.utils.Strings;
import com.orion.lang.utils.codec.Base64s;
import com.orion.lang.utils.convert.Converts;
import com.orion.lang.utils.io.FileWriters;
import com.orion.lang.utils.io.Files1;
import com.orion.net.remote.channel.SessionHolder;
import com.orion.ops.constant.Const;
import com.orion.ops.constant.MessageConst;
import com.orion.ops.constant.event.EventKeys;
import com.orion.ops.dao.MachineInfoDAO;
import com.orion.ops.dao.MachineSecretKeyDAO;
import com.orion.ops.entity.domain.MachineInfoDO;
import com.orion.ops.entity.domain.MachineSecretKeyDO;
import com.orion.ops.entity.request.machine.MachineKeyRequest;
import com.orion.ops.entity.vo.machine.MachineSecretKeyVO;
import com.orion.ops.service.api.MachineKeyService;
import com.orion.ops.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 机器密钥服务
 *
 * @author Jiahang Li
 * @version 1.0.0
 * @since 2021/4/5 11:20
 */
@Slf4j
@Service("machineKeyService")
public class MachineKeyServiceImpl implements MachineKeyService {

    @Resource
    private MachineSecretKeyDAO machineSecretKeyDAO;

    @Resource
    private MachineInfoDAO machineInfoDAO;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addSecretKey(MachineKeyRequest request) {
        MachineSecretKeyDO key = new MachineSecretKeyDO();
        key.setKeyName(request.getName());
        key.setDescription(request.getDescription());
        String file = PathBuilders.getSecretKeyPath();
        String path = MachineKeyService.getKeyPath(file);
        key.setSecretKeyPath(file);
        Files1.touch(path);
        byte[] keyFileData = Base64s.decode(Strings.bytes(request.getFile()));
        FileWriters.writeFast(path, keyFileData);
        String password = request.getPassword();
        if (Strings.isEmpty(password)) {
            key.setPassword(Const.EMPTY);
        } else {
            key.setPassword(ValueMix.encrypt(password));
        }
        // 检查密钥
        this.checkLoadKey(path, password);
        // 插入
        machineSecretKeyDAO.insert(key);
        // 设置日志参数
        EventParamsHolder.addParams(key);
        return key.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateSecretKey(MachineKeyRequest request) {
        // 查询key
        Long id = request.getId();
        MachineSecretKeyDO beforeKey = machineSecretKeyDAO.selectById(id);
        // 设置修改信息
        MachineSecretKeyDO updateKey = new MachineSecretKeyDO();
        updateKey.setId(id);
        updateKey.setKeyName(request.getName());
        updateKey.setDescription(request.getDescription());
        updateKey.setUpdateTime(new Date());
        String password = request.getPassword();
        String fileBase64 = request.getFile();
        // 修改文件
        final boolean updateFile = !Strings.isBlank(fileBase64);
        if (updateFile) {
            // 修改密钥文件 将新密钥保存到本地
            String keyFile = PathBuilders.getSecretKeyPath();
            String keyPath = MachineKeyService.getKeyPath(keyFile);
            Files1.touch(keyPath);
            byte[] keyFileData = Base64s.decode(Strings.bytes(fileBase64));
            FileWriters.writeFast(keyPath, keyFileData);
            updateKey.setSecretKeyPath(keyFile);
        }
        // 修改密码
        if (Strings.isBlank(password)) {
            updateKey.setPassword(Const.EMPTY);
        } else {
            updateKey.setPassword(ValueMix.encrypt(password));
        }
        // 检查密钥
        String checkPath = updateFile ? updateKey.getSecretKeyPath() : beforeKey.getSecretKeyPath();
        this.checkLoadKey(MachineKeyService.getKeyPath(checkPath), password);
        // 更新
        int effect = machineSecretKeyDAO.updateById(updateKey);
        // 设置日志参数
        EventParamsHolder.addParam(EventKeys.NAME, beforeKey.getKeyName());
        EventParamsHolder.addParams(updateKey);
        return effect;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer removeSecretKey(List<Long> idList) {
        // 删除密钥
        int effect = machineSecretKeyDAO.deleteBatchIds(idList);
        // 删除机器关联
        LambdaUpdateWrapper<MachineInfoDO> wrapper = new LambdaUpdateWrapper<MachineInfoDO>()
                .set(MachineInfoDO::getKeyId, null)
                .in(MachineInfoDO::getKeyId, idList);
        machineInfoDAO.update(null, wrapper);
        // 设置日志参数
        EventParamsHolder.addParam(EventKeys.ID_LIST, idList);
        EventParamsHolder.addParam(EventKeys.COUNT, idList.size());
        return effect;
    }

    @Override
    public MachineSecretKeyDO getKeyById(Long id) {
        return machineSecretKeyDAO.selectById(id);
    }

    @Override
    public DataGrid<MachineSecretKeyVO> listKeys(MachineKeyRequest request) {
        LambdaQueryWrapper<MachineSecretKeyDO> wrapper = new LambdaQueryWrapper<MachineSecretKeyDO>()
                .like(Strings.isNotBlank(request.getName()), MachineSecretKeyDO::getKeyName, request.getName())
                .like(Strings.isNotBlank(request.getDescription()), MachineSecretKeyDO::getDescription, request.getDescription())
                .orderByDesc(MachineSecretKeyDO::getCreateTime);
        return DataQuery.of(machineSecretKeyDAO)
                .page(request)
                .wrapper(wrapper)
                .dataGrid(MachineSecretKeyVO.class);
    }

    @Override
    public MachineSecretKeyVO getKeyDetail(Long id) {
        MachineSecretKeyDO key = machineSecretKeyDAO.selectById(id);
        Valid.notNull(key, MessageConst.UNKNOWN_DATA);
        return Converts.to(key, MachineSecretKeyVO.class);
    }

    @Override
    public void bindMachineKey(Long id, List<Long> machineIdList) {
        // 查询数据
        MachineSecretKeyDO key = machineSecretKeyDAO.selectById(id);
        Valid.notNull(key, MessageConst.UNKNOWN_DATA);
        // 更新到机器表
        LambdaUpdateWrapper<MachineInfoDO> wrapper = new LambdaUpdateWrapper<MachineInfoDO>()
                .set(MachineInfoDO::getKeyId, id)
                .in(MachineInfoDO::getId, machineIdList);
        machineInfoDAO.update(null, wrapper);
        // 设置日志参数
        EventParamsHolder.addParam(EventKeys.ID, id);
        EventParamsHolder.addParam(EventKeys.NAME, key.getKeyName());
        EventParamsHolder.addParam(EventKeys.MACHINE_ID_LIST, machineIdList);
        EventParamsHolder.addParam(EventKeys.COUNT, machineIdList.size());
    }

    /**
     * 检查密钥是否合法
     *
     * @param path     path
     * @param password 密码
     */
    private void checkLoadKey(String path, String password) {
        try {
            if (Strings.isEmpty(password)) {
                SessionHolder.HOLDER.addIdentity(path);
            } else {
                SessionHolder.HOLDER.addIdentity(path, password);
            }
            SessionHolder.HOLDER.removeAllIdentity();
        } catch (Exception e) {
            throw Exceptions.app(MessageConst.ILLEGAL_MACHINE_SECRET_KEY, e);
        }
    }

}
