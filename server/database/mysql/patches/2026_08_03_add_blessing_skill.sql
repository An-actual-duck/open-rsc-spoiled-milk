ALTER TABLE `_PREFIX_curstats`
	ADD `blessing` tinyint(3) UNSIGNED NOT NULL DEFAULT 1;

ALTER TABLE `_PREFIX_experience`
	ADD `blessing` int(9) NOT NULL DEFAULT 0;

ALTER TABLE `_PREFIX_maxstats`
	ADD `blessing` tinyint(3) UNSIGNED NOT NULL DEFAULT 1;

ALTER TABLE `_PREFIX_capped_experience`
	ADD `blessing` int(10) UNSIGNED;
