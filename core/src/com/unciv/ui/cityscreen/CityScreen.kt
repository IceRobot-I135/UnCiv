package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import java.util.*

class CityScreen(internal val city: CityInfo) : CameraStageBaseScreen() {
    private var selectedTile: TileInfo? = null

    private var tileTable = Table()
    private var buildingsTable = BuildingsTable(this)
    private var cityStatsTable = CityStatsTable(this)
    private var cityPickerTable = Table()
    private var goToWorldButton = TextButton("Exit city".tr(), CameraStageBaseScreen.skin)
    private var tileGroups = ArrayList<CityTileGroup>()

    init {
        onBackButtonClicked { UnCivGame.Current.setWorldScreen(); dispose() }
        addTiles()
        stage.addActor(tileTable)


        val tableBackgroundColor = ImageGetter.getBlue().lerp(Color.BLACK,0.5f)
        tileTable.background = ImageGetter.getBackground(tableBackgroundColor)

        val buildingsTableContainer = Table()
        buildingsTableContainer.pad(20f)
        buildingsTableContainer.background = ImageGetter.getBackground(tableBackgroundColor)
        buildingsTable.update()
        val buildingsScroll = ScrollPane(buildingsTable)
        buildingsTableContainer.add(buildingsScroll).height(stage.height / 2)

        buildingsTableContainer.pack()
        buildingsTableContainer.setPosition(stage.width - buildingsTableContainer.width,
                stage.height - buildingsTableContainer.height)

        cityStatsTable.background = ImageGetter.getBackground(tableBackgroundColor)
        stage.addActor(cityStatsTable)
        stage.addActor(goToWorldButton)
        stage.addActor(cityPickerTable)
        //stage.addActor(statExplainer)
        stage.addActor(buildingsTableContainer)
        update()
        displayTutorials("CityEntered")
    }

    internal fun update() {
        buildingsTable.update()
        updateCityPickerTable()
        cityStatsTable.update()
        updateGoToWorldButton()
        updateTileTable()
        updateTileGroups()

        if (city.getCenterTile().getTilesAtDistance(4).isNotEmpty()){
            displayTutorials("CityRange")
        }
    }

    private fun updateTileGroups() {
        val nextTile = city.expansion.chooseNewTileToOwn()
        for (tileGroup in tileGroups) {

            tileGroup.update()
            if(tileGroup.tileInfo == nextTile){
                tileGroup.showCircle(Color.PURPLE)
                tileGroup.setColor(0f,0f,0f,0.7f)
            }
        }

    }

    private fun updateCityPickerTable() {
        cityPickerTable.clear()
        cityPickerTable.row()

        val civInfo = city.civInfo
        if (civInfo.cities.size > 1) {
            val prevCityButton = TextButton("<", CameraStageBaseScreen.skin)
            prevCityButton.onClick {
                    val indexOfCity = civInfo.cities.indexOf(city)
                    val indexOfNextCity = if (indexOfCity == 0) civInfo.cities.size - 1 else indexOfCity - 1
                    game.screen = CityScreen(civInfo.cities[indexOfNextCity])
                    dispose()
                }
            cityPickerTable.add(prevCityButton).pad(20f)
        }

        if(city.isBeingRazed){
            val fireImage = ImageGetter.getImage("OtherIcons/Fire.png")
            cityPickerTable.add(fireImage).size(20f).padRight(5f)
        }

        if(city.isCapital()){
            val starImage = Image(ImageGetter.getDrawable("OtherIcons/Star.png").tint(Color.LIGHT_GRAY))
            cityPickerTable.add(starImage).size(20f).padRight(5f)
        }

        val currentCityLabel = Label(city.name+" ("+city.population.population+")", CameraStageBaseScreen.skin)
        currentCityLabel.setFontSize(25)
        cityPickerTable.add(currentCityLabel)


        if (civInfo.cities.size > 1) {
            val nextCityButton = TextButton(">", CameraStageBaseScreen.skin)
            nextCityButton.onClick {
                    val indexOfCity = civInfo.cities.indexOf(city)
                    val indexOfNextCity = if (indexOfCity == civInfo.cities.size - 1) 0 else indexOfCity + 1
                    game.screen = CityScreen(civInfo.cities[indexOfNextCity])
                    dispose()
                }
            cityPickerTable.add(nextCityButton).pad(20f)
        }
        cityPickerTable.row()

        if(!city.isBeingRazed) {
            val razeCityButton = TextButton("Raze city".tr(), skin)
            razeCityButton.onClick { city.isBeingRazed=true; update() }
            cityPickerTable.add(razeCityButton).colspan(cityPickerTable.columns)
        }
        else{
            val stopRazingCityButton = TextButton("Stop razing city".tr(), skin)
            stopRazingCityButton.onClick { city.isBeingRazed=false; update() }
            cityPickerTable.add(stopRazingCityButton).colspan(cityPickerTable.columns)
        }

        cityPickerTable.pack()
        cityPickerTable.centerX(stage)
        stage.addActor(cityPickerTable)
    }

    private fun updateGoToWorldButton() {
        goToWorldButton.clearListeners()
        goToWorldButton.onClick {
            game.setWorldScreen()
            game.worldScreen.tileMapHolder.setCenterPosition(city.location)
            game.worldScreen.bottomBar.unitTable.selectedUnit=null
            dispose()
        }

        goToWorldButton.pad(5f)
        goToWorldButton.setSize(goToWorldButton.prefWidth, goToWorldButton.prefHeight)
        goToWorldButton.setPosition(20f, stage.height - goToWorldButton.height - 20)
    }

    private fun addTiles() {
        val cityInfo = city

        val allTiles = Group()

        for (tileInfo in cityInfo.getCenterTile().getTilesInDistance(5)) {
            if (!city.civInfo.exploredTiles.contains(tileInfo.position)) continue // Don't even bother to display it.
            val tileGroup = CityTileGroup(cityInfo, tileInfo)
            val tilesInRange = city.getTilesInRange()

            // this needs to happen on update, because we can buy tiles, which changes the definition of the bought tiles...
            if (tileInfo.getCity()!=city) { // outside of city
                if(city.canAcquireTile(tileInfo)){
                    tileGroup.addAcquirableIcon()
                    tileGroup.yieldGroup.isVisible = false
                } else {
                    tileGroup.setColor(0f, 0f, 0f, 0.3f)
                    tileGroup.yieldGroup.isVisible = false
                }
            } else if(tileInfo !in tilesInRange){ // within city but not close enough to be workable
                tileGroup.yieldGroup.isVisible = false
            }
            else if (!tileInfo.isCityCenter() && tileGroup.populationImage==null) { // workable
                tileGroup.addPopulationIcon()
                tileGroup.onClick {
                    if (!tileInfo.isWorked() && city.population.getFreePopulation() > 0)
                        city.workedTiles.add(tileInfo.position)
                    else if (tileInfo.isWorked()) city.workedTiles.remove(tileInfo.position)
                    city.cityStats.update()
                    update()
                }
            }
            tileGroup.onClick {
                    selectedTile = tileInfo
                    update()
                }


            val positionalVector = HexMath().hex2WorldCoords(tileInfo.position.cpy().sub(cityInfo.location))
            val groupSize = 50
            tileGroup.setPosition(stage.width / 2 + positionalVector.x * 0.8f * groupSize.toFloat(),
                    stage.height / 2 + positionalVector.y * 0.8f * groupSize.toFloat())
            tileGroups.add(tileGroup)
            allTiles.addActor(tileGroup)
        }

        val scrollPane = ScrollPane(allTiles)
        scrollPane.setFillParent(true)
        scrollPane.setPosition(cityTilesX, cityTilesY)
        scrollPane.setOrigin(stage.width / 2, stage.height / 2)
        scrollPane.addListener(object : ActorGestureListener() {
            var lastScale = 1f
            var lastInitialDistance = 0f

            override fun zoom(event: InputEvent?, initialDistance: Float, distance: Float) {
                if (lastInitialDistance != initialDistance) {
                    lastInitialDistance = initialDistance
                    lastScale = scrollPane.scaleX
                }
                val scale = Math.sqrt((distance / initialDistance).toDouble()).toFloat() * lastScale
                scrollPane.setScale(scale)
            }

            override fun pan(event: InputEvent?, x: Float, y: Float, deltaX: Float, deltaY: Float) {
                scrollPane.moveBy(deltaX * scrollPane.scaleX, deltaY * scrollPane.scaleX)
                cityTilesX = scrollPane.x
                cityTilesY = scrollPane.y
            }
        })
        stage.addActor(scrollPane)
    }

    private fun updateTileTable() {
        if (selectedTile == null) return
        val tile = selectedTile!!
        tileTable.clearChildren()

        val stats = tile.getTileStats(city, city.civInfo)
        tileTable.pad(20f)

        tileTable.add(Label(tile.toString(), CameraStageBaseScreen.skin)).colspan(2)
        tileTable.row()

        val statsTable = Table()
        statsTable.defaults().pad(2f)
        for (entry in stats.toHashMap().filterNot { it.value==0f }) {
            statsTable.add(ImageGetter.getStatIcon(entry.key.toString())).size(20f)
            statsTable.add(Label(Math.round(entry.value).toString(), CameraStageBaseScreen.skin))
            statsTable.row()
        }
        tileTable.add(statsTable).row()

        if(tile.getOwner()==null && tile.neighbors.any{it.getCity()==city}){
            val goldCostOfTile = city.expansion.getGoldCostOfTile(tile)
            val buyTileButton = TextButton("Buy for [$goldCostOfTile] gold".tr(),skin)
            buyTileButton.onClick { city.expansion.buyTile(tile); game.screen = CityScreen(city); dispose() }
            if(goldCostOfTile>city.civInfo.gold) buyTileButton.disable()
            tileTable.add(buyTileButton)
        }
        if(city.canAcquireTile(tile)){
            val acquireTileButton = TextButton("Acquire".tr(),skin)
            acquireTileButton.onClick { city.expansion.takeOwnership(tile); game.screen = CityScreen(city); dispose() }
            tileTable.add(acquireTileButton)
        }

        tileTable.pack()
        tileTable.setPosition(stage.width - 10f - tileTable.width, 10f)
    }

    companion object {
        @Transient var cityTilesX = 0f
        @Transient var cityTilesY = 0f
    }
}

