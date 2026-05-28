ALTER TABLE `_PREFIX_curstats`
	ADD `summoning` tinyint(3) UNSIGNED NOT NULL DEFAULT 1;

ALTER TABLE `_PREFIX_experience`
	ADD `summoning` int(9) NOT NULL DEFAULT 0;

ALTER TABLE `_PREFIX_maxstats`
	ADD `summoning` tinyint(3) UNSIGNED NOT NULL DEFAULT 1;

ALTER TABLE `_PREFIX_capped_experience`
	ADD `summoning` int(10) UNSIGNED;
