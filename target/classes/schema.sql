CREATE TABLE IF NOT EXISTS `neises_virtualmarket`.`market` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `item_name` VARCHAR(256) NULL,
  `item_price` VARCHAR(256) NULL,
  `item_amount` VARCHAR(256) NULL,
  `user_name` VARCHAR(256) NULL,
  `user_uuid` VARCHAR(256) NULL
  PRIMARY KEY (`id`),
  UNIQUE INDEX `id_UNIQUE` (`id` ASC) VISIBLE)
ENGINE = InnoDB;