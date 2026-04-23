-- t_resource_common_ext was introduced as a transitional copy of common
-- resource extension fields, but the application never reads or writes it.
-- The authoritative fields remain on the type-specific extension tables.
DROP TABLE IF EXISTS `t_resource_common_ext`;
