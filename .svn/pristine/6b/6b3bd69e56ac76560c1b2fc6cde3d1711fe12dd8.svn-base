"""插件公共运行库的导出范围；宿主私有图标只能经图标服务访问。"""

COMPOSE_ABI = "1.6.1-host-icons-v1"
HOST_PRIVATE_PREFIXES = ("androidx/compose/material/icons/",)


def is_host_private_class(name):
    name = name.lstrip("[")
    if name.startswith("L") and name.endswith(";"):
        name = name[1:-1]
    return name.startswith(HOST_PRIVATE_PREFIXES)


def host_private_dex_types(dex):
    return sorted({dex.get_cm_type(index) for index in range(dex.header.type_ids_size)
                   if is_host_private_class(dex.get_cm_type(index))})
