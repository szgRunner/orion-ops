package com.orion.ops.service.impl;

import com.alibaba.fastjson.JSON;
import com.orion.lang.define.wrapper.HttpWrapper;
import com.orion.lang.id.UUIds;
import com.orion.lang.utils.Exceptions;
import com.orion.lang.utils.Strings;
import com.orion.lang.utils.io.Files1;
import com.orion.lang.utils.io.Streams;
import com.orion.ops.constant.Const;
import com.orion.ops.constant.KeyConst;
import com.orion.ops.constant.ResultCode;
import com.orion.ops.constant.download.FileDownloadType;
import com.orion.ops.dao.FileTailListDAO;
import com.orion.ops.dao.MachineSecretKeyDAO;
import com.orion.ops.entity.domain.FileTailListDO;
import com.orion.ops.entity.domain.FileTransferLogDO;
import com.orion.ops.entity.domain.MachineSecretKeyDO;
import com.orion.ops.entity.dto.file.FileDownloadDTO;
import com.orion.ops.handler.sftp.direct.DirectDownloader;
import com.orion.ops.service.api.*;
import com.orion.ops.utils.Currents;
import com.orion.web.servlet.web.Servlets;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Jiahang Li
 * @version 1.0.0
 * @since 2021/6/8 17:37
 */
@Service("fileDownloadService")
public class FileDownloadServiceImpl implements FileDownloadService {

    @Resource
    private MachineSecretKeyDAO machineSecretKeyDAO;

    @Resource
    private CommandExecService commandExecService;

    @Resource
    private MachineTerminalService machineTerminalService;

    @Resource
    private FileTailListDAO fileTailListDAO;

    @Resource
    private ApplicationActionLogService applicationActionLogService;

    @Resource
    private ApplicationBuildService applicationBuildService;

    @Resource
    private ApplicationReleaseMachineService applicationReleaseMachineService;

    @Resource
    private SchedulerTaskMachineRecordService schedulerTaskMachineRecordService;

    @Resource
    private SftpService sftpService;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public String getDownloadToken(Long id, FileDownloadType type) {
        String path = null;
        String name = null;
        Long machineId = Const.HOST_MACHINE_ID;
        // 获取日志绝对路径
        switch (type) {
            case SECRET_KEY:
                // 密钥
                path = this.getDownloadSecretKeyFilePath(id);
                name = Optional.ofNullable(path).map(Files1::getFileName).orElse(null);
                break;
            case TERMINAL_SCREEN:
                // terminal 录屏
                path = machineTerminalService.getTerminalScreenFilePath(id);
                name = Optional.ofNullable(path).map(Files1::getFileName).orElse(null);
                break;
            case EXEC_LOG:
                // 执行日志
                path = commandExecService.getExecLogFilePath(id);
                name = Optional.ofNullable(path).map(Files1::getFileName).orElse(null);
                break;
            case SFTP_DOWNLOAD:
                // sftp 下载文件
                FileTransferLogDO transferLog = sftpService.getDownloadFilePath(id);
                if (transferLog != null) {
                    path = transferLog.getLocalFile();
                    name = Files1.getFileName(transferLog.getRemoteFile());
                }
                break;
            case TAIL_LIST_FILE:
                // tail 列表文件
                FileTailListDO tailFile = fileTailListDAO.selectById(id);
                if (tailFile != null) {
                    path = tailFile.getFilePath();
                    name = Files1.getFileName(path);
                    machineId = tailFile.getMachineId();
                }
                break;
            case APP_BUILD_LOG:
                // 应用构建日志
                path = applicationBuildService.getBuildLogPath(id);
                name = Optional.ofNullable(path).map(Files1::getFileName).orElse(null);
                break;
            case APP_ACTION_LOG:
                // 应用构建操作日志
                path = applicationActionLogService.getActionLogPath(id);
                name = Optional.ofNullable(path).map(Files1::getFileName).orElse(null);
                break;
            case APP_BUILD_BUNDLE:
                // 应用构建 产物文件
                path = applicationBuildService.getBuildBundlePath(id);
                // 如果是文件夹则获取压缩文件
                if (path != null && Files1.isDirectory(path)) {
                    path += "." + Const.SUFFIX_ZIP;
                }
                name = Optional.ofNullable(path).map(Files1::getFileName).orElse(null);
                break;
            case APP_RELEASE_MACHINE_LOG:
                // 应用发布机器日志
                path = applicationReleaseMachineService.getReleaseMachineLogPath(id);
                name = Optional.ofNullable(path).map(Files1::getFileName).orElse(null);
                break;
            case SCHEDULER_TASK_MACHINE_LOG:
                // 调度任务机器日志
                path = schedulerTaskMachineRecordService.getTaskMachineLogPath(id);
                name = Optional.ofNullable(path).map(Files1::getFileName).orElse(null);
                break;
            default:
                break;
        }
        // 检查文件是否存在
        if (path == null || (Const.HOST_MACHINE_ID.equals(machineId) && !Files1.isFile(path))) {
            throw Exceptions.httpWrapper(HttpWrapper.of(ResultCode.FILE_MISSING));
        }
        // 设置缓存
        FileDownloadDTO download = new FileDownloadDTO();
        download.setFilePath(path);
        download.setFileName(Strings.def(name, Const.UNKNOWN));
        download.setUserId(Currents.getUserId());
        download.setType(type.getType());
        download.setMachineId(machineId);
        String token = UUIds.random19();
        String key = Strings.format(KeyConst.FILE_DOWNLOAD_TOKEN, token);
        redisTemplate.opsForValue().set(key, JSON.toJSONString(download),
                KeyConst.FILE_DOWNLOAD_EXPIRE, TimeUnit.SECONDS);
        return token;
    }

    @Override
    public FileDownloadDTO getPathByDownloadToken(String token) {
        if (Strings.isBlank(token)) {
            return null;
        }
        String key = Strings.format(KeyConst.FILE_DOWNLOAD_TOKEN, token);
        String value = redisTemplate.opsForValue().get(key);
        if (Strings.isBlank(value)) {
            return null;
        }
        FileDownloadDTO download = JSON.parseObject(value, FileDownloadDTO.class);
        if (download == null) {
            return null;
        }
        redisTemplate.delete(key);
        return download;
    }

    @Override
    public void execDownload(String token, HttpServletResponse response) throws IOException {
        // 获取token信息
        FileDownloadDTO downloadFile = this.getPathByDownloadToken(token);
        if (downloadFile == null) {
            throw Exceptions.notFound();
        }
        Long machineId = downloadFile.getMachineId();
        InputStream inputStream = null;
        DirectDownloader downloader = null;
        try {
            if (Const.HOST_MACHINE_ID.equals(machineId)) {
                // 本地文件
                File file = Optional.of(downloadFile)
                        .map(FileDownloadDTO::getFilePath)
                        .filter(Strings::isNotBlank)
                        .map(File::new)
                        .filter(Files1::isFile)
                        .orElseThrow(Exceptions::notFound);
                inputStream = Files1.openInputStreamFastSafe(file);
            } else {
                // 远程文件
                downloader = new DirectDownloader(machineId);
                inputStream = downloader.open().getFile(downloadFile.getFilePath());
            }
            // 返回
            Servlets.transfer(response, inputStream, downloadFile.getFileName());
        } finally {
            Streams.close(inputStream);
            Streams.close(downloader);
        }
    }

    /**
     * 获取下载 密钥路径
     *
     * @param id id
     * @return path
     * @see FileDownloadType#SECRET_KEY
     */
    private String getDownloadSecretKeyFilePath(Long id) {
        return Optional.ofNullable(machineSecretKeyDAO.selectById(id))
                .map(MachineSecretKeyDO::getSecretKeyPath)
                .map(MachineKeyService::getKeyPath)
                .filter(Strings::isNotBlank)
                .orElse(null);
    }

}
