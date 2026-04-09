-- Zip 技能包与 skill-artifact 下载链路已移除（见 V35）；埋点表无写入方，DROP 以免误读。

DROP TABLE IF EXISTS `t_skill_pack_download_event`;
