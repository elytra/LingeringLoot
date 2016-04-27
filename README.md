This mod lets you customize the despawn time of dropped items, with fine-grained granularity.
See the default config file below.

# Configuration file

##########################################################################################################
# despawn times
#--------------------------------------------------------------------------------------------------------#
# Despawn times are in seconds.  Minecraft's default is 300.  Use -1 to defer to less granular settings
# eg: player drops and player-killed mob drops are both types of mob drops, and player-caused drops.
# The order of precedence is: player drops, player-killed mob drops or player-mined items or player-thrown
# items, player-caused drops, mob drops, and finally other.
##########################################################################################################

"despawn times" {
    I:"mob drops"=-1
    I:other=900
    I:"player drops"=3600
    I:"player-caused drops"=1800
    I:"player-killed mob drops"=-1
    I:"player-mined items"=-1
    I:"player-thrown items"=-1
}


##########################################################################################################
# shit tier
#--------------------------------------------------------------------------------------------------------#
# The despawn time for shit-tier items, if set, overrides all other settings.
##########################################################################################################

"shit tier" {
    I:"shit despawn time"=300
    S:"shit tier items"=cobblestone,snowball
}
