-- CommunityDragon에서 받은 롤 챔피언 레벨당 공격력. 버전마다 한 번만 받는다 (Data Dragon이 16.5.1부터 0으로 줌)
CREATE TABLE champion_ad_per_level (
    version  VARCHAR(20)    NOT NULL,   -- Data Dragon 버전 (16.22.1)
    champion VARCHAR(50)    NOT NULL,   -- Data Dragon 영문 ID
    value    NUMERIC(10, 4) NOT NULL,
    PRIMARY KEY (version, champion)
);
